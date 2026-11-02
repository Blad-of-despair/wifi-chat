package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

class NsdDiscoveryManager(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val serviceType = "_homelink._tcp."
    private val tag = "HomeLinkNSD"

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private var isRegistered = false

    fun registerHub(
        serviceName: String,
        port: Int,
        attributes: Map<String, String>,
        onRegistered: (registeredName: String) -> Unit = {}
    ) {
        if (nsdManager == null) return
        unregisterHub()

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = this@NsdDiscoveryManager.serviceType
            this.port = port
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                attributes.forEach { (key, value) ->
                    setAttribute(key, value)
                }
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(tag, "Hub registered successfully: ${info.serviceName}")
                isRegistered = true
                onRegistered(info.serviceName)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Hub registration failed: $errorCode")
                isRegistered = false
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(tag, "Hub unregistered: ${info.serviceName}")
                isRegistered = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Hub unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Exception registering NSD service", e)
        }
    }

    fun unregisterHub() {
        if (nsdManager != null && isRegistered && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener)
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering service", e)
            }
            registrationListener = null
            isRegistered = false
        }
    }

    fun startDiscovery(
        onDiscovered: (serviceName: String, host: String, port: Int, attributes: Map<String, String>) -> Unit
    ) {
        if (nsdManager == null || isDiscovering) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "NSD discovery started: $regType")
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}, resolving...")
                try {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(tag, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            val port = serviceInfo.port
                            val attrs = mutableMapOf<String, String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                serviceInfo.attributes?.forEach { (k, v) ->
                                    attrs[k] = v?.let { String(it, Charsets.UTF_8) } ?: ""
                                }
                            }
                            Log.d(tag, "Resolved Hub: $host:$port attrs=$attrs")
                            onDiscovered(serviceInfo.serviceName, host, port, attrs)
                        }
                    })
                } catch (e: Exception) {
                    Log.e(tag, "Error in resolveService", e)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "Discovery stopped")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Start discovery failed: $errorCode")
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error starting discovery", e)
        }
    }

    fun stopDiscovery() {
        if (nsdManager != null && isDiscovering && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping discovery", e)
            }
            discoveryListener = null
            isDiscovering = false
        }
    }
}
