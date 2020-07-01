package com.gjung.haifa3d.ui.presets

import android.os.Bundle
import android.os.UserManager
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.gjung.haifa3d.BleFragment

import com.gjung.haifa3d.R
import com.gjung.haifa3d.adapter.MovementsAdapter
import com.gjung.haifa3d.adapter.PresetsAdapter
import com.gjung.haifa3d.ble.IDirectExecuteService
import com.gjung.haifa3d.ble.IPresetService
import com.gjung.haifa3d.databinding.FragmentEditPresetBinding
import com.gjung.haifa3d.getNavigationResultLiveData
import com.gjung.haifa3d.model.*
import com.gjung.haifa3d.notifyObserver
import com.gjung.haifa3d.util.InjectorUtils
import kotlinx.coroutines.*

/**
 * A simple [Fragment] subclass.
 */
class EditPresetFragment : BleFragment(), MovementsAdapter.OnItemClickListener {
    private lateinit var binding: FragmentEditPresetBinding
    private var presetService: IPresetService? = null
    private var directExecuteService: IDirectExecuteService? = null
    private val args: EditPresetFragmentArgs by navArgs()
    private val presetsViewModel: PresetsViewModel by activityViewModels {
        InjectorUtils.providePresetsViewModelFactory(requireContext())
    }
    private lateinit var adapter: MovementsAdapter
    private val movements
        get() = presetsViewModel.presets.value!![args.presetId].handAction!!.Movements

    override fun onServiceConnected() {
        presetService = bleService!!.manager.presetService
        directExecuteService = bleService!!.manager.directExecuteService
    }

    override fun onServiceDisconnected() {
        presetService = null
        directExecuteService = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.edit_preset, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_try_preset -> {
                tryPreset()
                true
            }
            R.id.action_save_hand_action -> {
                saveHandAction()
                true
            }
            R.id.action_add_hand_movement -> {
                addHandMovement()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun tryPreset() {
        directExecuteService?.executeAction(HandAction(movements))
    }

    private fun addHandMovement() {
        movements.add(HandMovement(
            TorqueStopModeDetail(TorqueStopThreshold.Low),
            TimeStopModeDetail(50u),
            MotorsActivated(
                turn = true,
                finger1 = true
            ),
            MotorsDirection(
                turn = MotorDirection.Dir1,
                finger1 = MotorDirection.Dir1
            )
        ))
        adapter.notifyItemInserted(movements.size)
        presetsViewModel.presets.notifyObserver()
    }

    private fun saveHandAction() {
        GlobalScope.launch(Dispatchers.IO) {
            presetService!!.writePreset(args.presetId, HandAction(movements))
            var name: String? = binding.presetNameEdit.text.toString()
            if (name.isNullOrBlank())
                name = null
            presetsViewModel.setPresetName(args.presetId, HandAction(movements), name)
            withContext(Dispatchers.Main) {
                val navController = this@EditPresetFragment.findNavController();
                navController.navigateUp()
            }
        }
    }

    override fun onItemClick(movementIndex: Int, movement: HandMovement) {
        val act = EditPresetFragmentDirections.editMovement(args.presetId, movementIndex)
        this.findNavController().navigate(act)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEditPresetBinding.inflate(layoutInflater, container, false)
        setHasOptionsMenu(true)

        adapter = MovementsAdapter(presetsViewModel, args.presetId)

        val rec = binding.recyclerViewMovements
        rec.adapter = adapter
        rec.setHasFixedSize(true)
        rec.layoutManager = LinearLayoutManager(this.requireContext())
        rec.addItemDecoration(DividerItemDecoration(this.requireContext(), DividerItemDecoration.VERTICAL))

        adapter.onItemClickListener = this

        Transformations.switchMap(presetsViewModel.presetNames) { names ->
            Transformations.map(presetsViewModel.presets) { presets ->
                names[presets[args.presetId]]
            }
        }.observe(viewLifecycleOwner, Observer {
            binding.presetNameEdit.setText(it)
        })

        return binding.root
    }

}
