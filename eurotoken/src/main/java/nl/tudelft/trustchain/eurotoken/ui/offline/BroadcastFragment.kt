package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
import android.view.View
import android.widget.Toast
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.community.EuroTokenCommunity
import nl.tudelft.trustchain.eurotoken.databinding.FragmentBroadcastBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import nl.tudelft.trustchain.eurotoken.community.BroadcastMessage
import androidx.lifecycle.Observer

class BroadcastFragment:EurotokenBaseFragment(R.layout.fragment_broadcast) {

    private val binding by viewBinding(FragmentBroadcastBinding::bind)

    private inner class BroadcastAdapter :
        RecyclerView.Adapter<BroadcastAdapter.VH>() {

        private val items = mutableListOf<BroadcastMessage>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val txtSender  = view.findViewById<TextView>(android.R.id.text1)
            val txtMessage = view.findViewById<TextView>(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val bm = items[position]
            holder.txtSender.text  = bm.senderId
            holder.txtMessage.text = bm.message
        }

        fun submitList(newList: List<BroadcastMessage>) {
            items.clear()
            items.addAll(newList)
            notifyDataSetChanged()
        }
    }




    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ➍ Listener del bottone
        val community = getIpv8().getOverlay<EuroTokenCommunity>()!!

        // ➋ Setta il RecyclerView
        val adapter = BroadcastAdapter()
        binding.rvBroadcasts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBroadcasts.adapter = adapter

        // ➌ Osserva la LiveData dei messaggi
        community.bluetoothBroadcasts.observe(viewLifecycleOwner, Observer { list ->
            adapter.submitList(list)
        })

        // ➍ Listener del bottone (invia)
        binding.btnSendHello.setOnClickListener {
            val message = binding.editMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                community.broadcastBluetoothMessage(message)
                Toast.makeText(
                    requireContext(),
                    "$message inserted", Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Insert a message", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}














