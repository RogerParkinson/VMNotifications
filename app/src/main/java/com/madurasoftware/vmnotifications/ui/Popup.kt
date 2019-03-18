package com.madurasoftware.vmnotifications.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.madurasoftware.vmnotifications.R

class Popup(private val activity: Activity) {

    @SuppressLint("InflateParams")
    fun showPopup(anchorView: View) {

        val layoutinflator = activity.getLayoutInflater()
        val popupView = layoutinflator.inflate(R.layout.paired_popup, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val btArrayAdapter = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1)
        val devicesListView = popupView.findViewById(R.id.devicesListView) as ListView
        devicesListView.setAdapter(btArrayAdapter) // assign model to view
        devicesListView.setOnItemClickListener(AdapterView.OnItemClickListener { av, v, arg2, arg3 ->
            popupWindow.dismiss()
            if (!isBluetoothSupported()) {
                Toast.makeText(activity, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show()
                return@OnItemClickListener
            }
            // Get the device MAC address, which is the last 17 chars in the View
            val info = (v as TextView).text.toString()
            connectToAddress(anchorView.context,info)
        })
        if (!(listPairedDevices(activity, btArrayAdapter))) {
            return
        }

        // If the PopupWindow should be focusable
        popupWindow.setFocusable(true)

        // If you need the PopupWindow to dismiss when when touched outside
        popupWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        popupWindow.setTouchInterceptor(View.OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                popupWindow.dismiss()
                return@OnTouchListener true
            }
            false
        })
        popupWindow.setOutsideTouchable(true)
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0,0)
    }
}