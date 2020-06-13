package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Preset
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_preset_list.view.*
import kotlinx.android.synthetic.main.layout_list_item__preset.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PresetFragment : Fragment() {

  private var mRecyclerView: RecyclerView? = null
  private var adapter: PresetListAdapter? = null
  private var activePresetPos = -1

  private val dataSet = ArrayList<Preset>()
  private val eventBus = EventBus.getDefault()
  private val mAdapterDataObserver = object : RecyclerView.AdapterDataObserver() {
    override fun onChanged() {
      if (adapter?.itemCount ?: 0 > 0) {
        requireView().indicator_list_empty.visibility = View.GONE
      } else {
        requireView().indicator_list_empty.visibility = View.VISIBLE
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_preset_list, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    dataSet.addAll(Preset.readAllFromUserPreferences(requireContext()))
    adapter = PresetListAdapter(requireContext()).apply {
      registerAdapterDataObserver(mAdapterDataObserver)
    }

    mRecyclerView = view.list_presets.also {
      it.setHasFixedSize(true)
      it.adapter = adapter
    }

    eventBus.register(this)
    mAdapterDataObserver.onChanged() // since observer is not called by adapter on initialization
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    adapter?.unregisterAdapterDataObserver(mAdapterDataObserver)
    super.onDestroyView()
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onPlayerManagerUpdate(event: MediaPlayerService.OnPlayerManagerUpdateEvent) {
    val oldPresetPos = activePresetPos
    activePresetPos = Preset.from("", event.players.values).let { dataSet.indexOf(it) }

    if (activePresetPos != oldPresetPos) {
      adapter?.notifyItemChanged(oldPresetPos)

      if (activePresetPos > -1) {
        adapter?.notifyItemChanged(activePresetPos)
      }
    }
  }

  inner class PresetListAdapter(context: Context) : RecyclerView.Adapter<ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(layoutInflater.inflate(R.layout.layout_list_item__preset, parent, false))
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      holder.itemView.title.text = dataSet[position].name
      holder.itemView.button_play.setImageResource(
        if (position == activePresetPos) {
          R.drawable.ic_action_stop
        } else {
          R.drawable.ic_action_play
        }
      )
    }
  }

  inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    init {
      itemView.button_play.setOnClickListener {
        if (adapterPosition != activePresetPos) {
          eventBus.post(MediaPlayerService.PlayPresetEvent(dataSet[adapterPosition]))
        } else {
          eventBus.post(MediaPlayerService.StopPlaybackEvent())
        }
      }

      val onMenuItemClickListener = PopupMenu.OnMenuItemClickListener {
        when (it.itemId) {
          R.id.action_delete -> showDeletePresetConfirmation()
          R.id.action_rename -> showRenamePresetInput()
        }

        true
      }

      itemView.button_menu.setOnClickListener {
        PopupMenu(requireContext(), itemView.button_menu).let {
          it.inflate(R.menu.preset)
          it.setOnMenuItemClickListener(onMenuItemClickListener)
          it.show()
        }
      }
    }

    private fun showRenamePresetInput() {
      DialogFragment().show(requireActivity().supportFragmentManager) {
        title(R.string.rename)
        input(
          hintRes = R.string.name,
          preFillValue = dataSet[adapterPosition].name,
          errorRes = R.string.preset_name_cannot_be_empty
        )
        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          dataSet[adapterPosition].name = getInputText()
          Preset.writeAllToUserPreferences(requireContext(), dataSet)
          adapter?.notifyItemChanged(adapterPosition)
        }
      }
    }

    private fun showDeletePresetConfirmation() {
      DialogFragment().show(requireActivity().supportFragmentManager) {
        title(R.string.delete)
        message(R.string.preset_delete_confirmation, dataSet[adapterPosition].name)
        negativeButton(R.string.cancel)
        positiveButton(R.string.delete) {
          dataSet.removeAt(adapterPosition)
          Preset.writeAllToUserPreferences(requireContext(), dataSet)
          // then stop playback if recently deleted preset was playing
          if (adapterPosition == activePresetPos) {
            eventBus.post(MediaPlayerService.StopPlaybackEvent())
          }

          if (adapterPosition < activePresetPos) {
            activePresetPos -= 1 // account for recent deletion
          }

          adapter?.notifyItemRemoved(adapterPosition)
          Snackbar.make(requireView(), R.string.preset_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.dismiss) { }
            .show()
        }
      }
    }
  }
}
