package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_sound_list.view.*
import kotlinx.android.synthetic.main.layout_list_item__sound.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SoundLibraryFragment : Fragment(R.layout.fragment_sound_list) {

  private var mRecyclerView: RecyclerView? = null
  private var mSavePresetButton: FloatingActionButton? = null
  private var adapter: SoundListAdapter? = null
  private var players = emptyMap<String, Player>()

  private val eventBus = EventBus.getDefault()

  private val dataSet by lazy {
    arrayListOf<SoundListItem>().also { list ->
      var lastDisplayGroupResID = -1
      val sounds = Sound.LIBRARY.toSortedMap(
        compareBy(
          { getString(Sound.get(it).displayGroupResID) },
          { getString(Sound.get(it).titleResId) }
        )
      )

      sounds.forEach {
        if (lastDisplayGroupResID != it.value.displayGroupResID) {
          lastDisplayGroupResID = it.value.displayGroupResID
          list.add(
            SoundListItem(
              R.layout.layout_list_item__sound_group_title, getString(lastDisplayGroupResID)
            )
          )
        }

        list.add(SoundListItem(R.layout.layout_list_item__sound, it.key))
      }
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
  fun onPlayerManagerUpdate(event: MediaPlayerService.OnPlayerManagerUpdateEvent) {
    this.players = event.players
    var showSavePresetFAB: Boolean
    Preset.readAllFromUserPreferences(requireContext()).also {
      showSavePresetFAB = !it.contains(Preset.from("", players.values))
    }

    view?.post {
      adapter?.notifyDataSetChanged()
      if (mSavePresetButton != null) {
        if (showSavePresetFAB && event.state == PlayerManager.State.PLAYING) {
          mSavePresetButton?.show()
        } else {
          mSavePresetButton?.hide()
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    adapter = SoundListAdapter(requireContext())
    mRecyclerView = view.list_sound.also {
      it.setHasFixedSize(true)
      it.adapter = adapter
    }

    mSavePresetButton = view.fab_save_preset
    requireNotNull(mSavePresetButton).setOnClickListener {
      DialogFragment().show(childFragmentManager) {
        val duplicateNameValidator = Preset.duplicateNameValidator(requireContext())
        title(R.string.save_preset)
        input(hintRes = R.string.name, validator = {
          when {
            it.isBlank() -> R.string.preset_name_cannot_be_empty
            duplicateNameValidator(it) -> R.string.preset_already_exists
            else -> 0
          }
        })

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          val preset = Preset.from(getInputText(), players.values)
          Preset.appendToUserPreferences(requireContext(), preset)
          mSavePresetButton?.hide()
          showPresetSavedMessage()
        }
      }
    }

    eventBus.register(this)
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    super.onDestroyView()
  }

  private fun showPresetSavedMessage() {
    Snackbar.make(requireView(), R.string.preset_saved, Snackbar.LENGTH_LONG)
      .setAction(R.string.dismiss) { }
      .show()
  }

  private class SoundListItem(@LayoutRes val layoutID: Int, val data: String)

  private inner class SoundListAdapter(private val context: Context) :
    RecyclerView.Adapter<ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(layoutInflater.inflate(viewType, parent, false), viewType)
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
      return dataSet[position].layoutID
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      if (dataSet[position].layoutID == R.layout.layout_list_item__sound_group_title) {
        holder.itemView as TextView
        holder.itemView.text = dataSet[position].data
        return
      }

      val soundKey = dataSet[position].data
      val sound = Sound.get(soundKey)
      val isPlaying = players.containsKey(soundKey)
      var volume = Player.DEFAULT_VOLUME
      var timePeriod = Player.DEFAULT_TIME_PERIOD
      if (isPlaying) {
        requireNotNull(players[soundKey]).also {
          volume = it.volume
          timePeriod = it.timePeriod
        }
      }

      holder.itemView.button_play.isChecked = isPlaying
      holder.itemView.title.text = context.getString(sound.titleResId)
      holder.itemView.slider_volume.isEnabled = isPlaying
      holder.itemView.slider_volume.value = volume.toFloat()
      holder.itemView.slider_time_period.isEnabled = isPlaying
      holder.itemView.slider_time_period.value = timePeriod.toFloat()
      holder.itemView.layout_time_period.visibility = if (sound.isLooping) {
        View.GONE
      } else {
        View.VISIBLE
      }
    }
  }

  inner class ViewHolder(view: View, @LayoutRes layoutID: Int) : RecyclerView.ViewHolder(view) {

    // set listeners in holders to avoid object recreation on view recycle
    private val sliderChangeListener = object : Slider.OnChangeListener {
      override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) {
          return
        }

        val player = players[dataSet[adapterPosition].data] ?: return
        when (slider.id) {
          R.id.slider_volume -> {
            player.setVolume(value.toInt())
          }
          R.id.slider_time_period -> {
            player.timePeriod = value.toInt()
          }
        }
      }
    }

    // forces update to save preset button (by invoking onPlayerManagerUpdate)
    private val sliderTouchListener = object : Slider.OnSliderTouchListener {
      override fun onStartTrackingTouch(slider: Slider) = Unit

      override fun onStopTrackingTouch(slider: Slider) {
        eventBus.getStickyEvent(MediaPlayerService.OnPlayerManagerUpdateEvent::class.java).also {
          it ?: return
          onPlayerManagerUpdate(it)
        }
      }
    }

    init {
      if (layoutID == R.layout.layout_list_item__sound) {
        initSoundItem(view)
      }
    }

    private fun initSoundItem(view: View) {
      setupSlider(view.slider_volume, 0, Player.MAX_VOLUME) {
        "${(it * 100).toInt() / Player.MAX_VOLUME}%"
      }

      setupSlider(view.slider_time_period, Player.MIN_TIME_PERIOD, Player.MAX_TIME_PERIOD) {
        "${it.toInt() / 60}m ${it.toInt() % 60}s"
      }

      view.button_play.setOnClickListener {
        val listItem = dataSet.getOrNull(adapterPosition) ?: return@setOnClickListener
        if (players.containsKey(listItem.data)) {
          eventBus.post(MediaPlayerService.StopPlayerEvent(listItem.data))
        } else {
          eventBus.post(MediaPlayerService.StartPlayerEvent(listItem.data))
        }
      }
    }

    private fun setupSlider(slider: Slider, from: Int, to: Int, formatter: (Float) -> String) {
      slider.valueFrom = from.toFloat()
      slider.valueTo = to.toFloat()
      slider.setLabelFormatter(formatter)
      slider.addOnChangeListener(sliderChangeListener)
      slider.addOnSliderTouchListener(sliderTouchListener)
    }
  }
}
