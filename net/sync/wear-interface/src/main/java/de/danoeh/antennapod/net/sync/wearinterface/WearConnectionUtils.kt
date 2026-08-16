package de.danoeh.antennapod.net.sync.wearinterface

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.ExecutionException

class WearConnectionUtils private constructor() {
    companion object {
        private const val TAG = "WearConnectionUtils"

        /**
         * Returns true if a reachable phone with the matching capability is connected.
         * The capability is declared in the phone's wear.xml and versioned, so both sides
         * can negotiate compatibility when updated independently.
         */
        @JvmStatic
        fun isPhoneSupported(context: Context): Boolean {
            try {
                val capability = context.getString(R.string.wear_capability_phone)
                val nodes = Tasks.await(
                        Wearable.getCapabilityClient(context)
                                .getCapability(capability, CapabilityClient.FILTER_REACHABLE))
                        .nodes
                return !nodes.isEmpty()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Failed to check phone capability", e)
                return false
            } catch (e: ExecutionException) {
                Log.w(TAG, "Failed to check phone capability", e)
                return false
            }
        }

        @JvmStatic
        fun getConnectedNodeName(context: Context): String {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isEmpty()) {
                    return ""
                }
                return nodes[0].displayName
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Failed to get connected node name", e)
                return ""
            } catch (e: ExecutionException) {
                Log.w(TAG, "Failed to get connected node name", e)
                return ""
            }
        }
    }
}
