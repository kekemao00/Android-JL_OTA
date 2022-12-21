package com.jieli.otasdk.fragments


import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ajguan.library.EasyRefreshLayout
import com.ajguan.library.LoadModel
import com.jieli.component.utils.ValueUtil
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.util.BluetoothUtil
import com.jieli.jl_dialog.Jl_Dialog
import com.jieli.otasdk.R
import com.jieli.otasdk.activities.MainActivity
import com.jieli.otasdk.tool.DevScanPresenter
import com.jieli.otasdk.tool.IDeviceContract
import com.jieli.otasdk.util.AutoTestTaskManager
import com.jieli.otasdk.util.JL_Constant
import com.jieli.otasdk.widget.SpecialDecoration
import kotlinx.android.synthetic.main.fragment_scan.*

class ScanFragment : Fragment(), IDeviceContract.IDevScanView {

    var mPresenter: DevScanPresenter? = null
    private var mNotifyDialog: Jl_Dialog? = null
    private var adapter: ScanDeviceAdapter? = null

    private var activity: MainActivity? = null
    val mHandler: Handler = Handler(Looper.getMainLooper())

    var isRefreshing: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (activity == null && context is MainActivity) {
            activity = context
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (activity == null && getActivity() is MainActivity) {
            activity = getActivity() as MainActivity
        }
        mPresenter = DevScanPresenter(this)
        AutoTestTaskManager.instance.devScanPresenter = mPresenter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        init()
    }

    override fun onStart() {
        super.onStart()
        mPresenter?.start()
    }

    private fun init() {
        adapter = ScanDeviceAdapter(mutableListOf())
        rc_device_list.adapter = adapter
        rc_device_list.layoutManager = LinearLayoutManager(activity)

        val color = resources.getColor(R.color.rc_decoration)
        rc_device_list.addItemDecoration(
            SpecialDecoration(
                activity,
                LinearLayoutManager.VERTICAL,
                color,
                ValueUtil.dp2px(activity!!, 1)
            )
        )
        adapter?.let {
            it.setOnItemClickListener { a, view, position ->
                val currentDevice = mPresenter?.getConnectedDevice()
                val device: BluetoothDevice? = adapter?.getItem(position)
                if (currentDevice != null && BluetoothUtil.deviceEquals(currentDevice, device)) {
                    mPresenter?.disconnectBtDevice(device)
                } else {
                    mPresenter?.stopScan()
                    mPresenter?.connectBtDevice(device!!)
                }
            }
        }

        easy_refresh.addEasyEvent(object : EasyRefreshLayout.EasyEvent {
            override fun onLoadMore() {

            }

            override fun onRefreshing() {
                if (!isRefreshing) {
                    adapter?.data?.clear()
                    mPresenter?.startScan()
                    isRefreshing = true

                    mHandler.postDelayed({
                        if (isRefreshing) {
                            easy_refresh.refreshComplete()
                            isRefreshing = false
                        }
                    }, 500)
                }
            }
        })
        easy_refresh.loadMoreModel = LoadModel.NONE

    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPresenter?.destroy()
    }

    override fun setPresenter(presenter: IDeviceContract.IDevScanPresenter) {
        mPresenter ?: presenter
    }

    override fun onScanStatus(status: Int, device: BluetoothDevice?) {
        getAutoTestView()?.onScanStatus(status, device)
        if (isDetached || !isAdded) return
        when (status) {
            JL_Constant.SCAN_STATUS_FOUND_DEV -> {
                device.let {
                    val filter = ed_scan_ble_filter.text.toString()
                    if (!TextUtils.isEmpty(it?.name) && (TextUtils.isEmpty(filter)
                                || it?.name!!.contains(filter))
                    ) {
                        adapter?.addDevice(it)
                    }
                }
            }
            else -> {
                val bStart = status == JL_Constant.SCAN_STATUS_SCANNING
                if (bStart) {
                    adapter?.apply {
                        this.data.clear()
                        mPresenter?.getConnectedDevice()?.let {
                            this.data.add(it)
                        }
                        this.notifyDataSetChanged()
                    }
                }

                activity?.let {
                    it.findViewById<View>(R.id.bar_scan_status)?.visibility =
                        if (bStart) View.VISIBLE else View.INVISIBLE
                }

                tv_scan_tip?.text =
                    if (bStart) getString(R.string.scaning_tip) else getString(R.string.scan_tip)
            }
        }
    }

    override fun onConnectStatus(device: BluetoothDevice?, status: Int) {
        getAutoTestView()?.onConnectStatus(device, status)
        when (status) {
            StateCode.CONNECTION_CONNECTING -> showConnectionDialog()
            else -> {
                if (status == StateCode.CONNECTION_CONNECTED) {
                    device?.let {
                        adapter?.addDevice(it)
                    }
                }
                dismissConnectionDialog()
                mPresenter?.startScan()
            }
        }
    }

    override fun onMandatoryUpgrade() {
        getAutoTestView()?.onMandatoryUpgrade()
        activity?.switchSubFragment(0, false)
    }

    override fun onErrorCallback(code: Int, message: String) {
        getAutoTestView()?.onErrorCallback(code, message)
        if (code == 10596) {
            kotlin.run { tv_scan_tip.text = getString(R.string.bt_bluetooth_close) }
        }
    }


    private fun showConnectionDialog() {
        if (mNotifyDialog == null) {
            mNotifyDialog = Jl_Dialog.builder()
                .title(getString(R.string.tips))
                .content(getString(R.string.bt_connecting))
                .showProgressBar(true)
                .width(0.8f)
                .cancel(false)
                .build()
        }

        mNotifyDialog?.let {
            if (!it.isShow) {
                it.show(activity!!.supportFragmentManager, "connect_to_ble")
            }
        }
    }

    private fun dismissConnectionDialog() {
        mNotifyDialog?.let {
            if (it.isShow) {
                it.dismiss()
            }

        }
    }

    private fun getAutoTestView(): IDeviceContract.IDevScanView? {
        AutoTestTaskManager.instance.currentTestTask?.let {
            return it as? IDeviceContract.IDevScanView
        }
        return null
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        if (isVisibleToUser) {
            mPresenter?.startScan()
        } else {
            mPresenter?.stopScan()
        }
    }


}
