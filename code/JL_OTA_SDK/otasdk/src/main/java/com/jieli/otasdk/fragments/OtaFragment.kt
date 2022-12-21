package com.jieli.otasdk.fragments


import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jieli.component.ui.CommonDecoration
import com.jieli.component.utils.FileUtil
import com.jieli.component.utils.ToastUtil
import com.jieli.jl_bt_ota.constant.ErrorCode
import com.jieli.jl_bt_ota.util.JL_Log
import com.jieli.jl_bt_ota.util.PreferencesHelper
import com.jieli.otasdk.R
import com.jieli.otasdk.tool.IOtaContract
import com.jieli.otasdk.tool.OtaPresenter
import com.jieli.otasdk.util.AutoTestTaskManager
import com.jieli.otasdk.util.JL_Constant
import com.jieli.otasdk.util.OtaFileObserverHelper
import com.jieli.otasdk.util.auto_test.AutoTestStatisticsUtil
import kotlinx.android.synthetic.main.fragment_ota.*
import java.io.File
import kotlin.math.roundToInt


class OtaFragment : Fragment(), IOtaContract.IOtaView {

    private var otaHelper: OtaPresenter? = null
    private var adapter: FileAdapter? = null
    private var autoTestStatisticsUtil: AutoTestStatisticsUtil = AutoTestStatisticsUtil()

    companion object {
        private const val MSG_UPDATE_OTA_FILE_LIST = 0x01
    }

    private val mHandler: Handler = Handler(Handler.Callback {
        when (it.what) {
            MSG_UPDATE_OTA_FILE_LIST -> readFileList()
        }
        return@Callback false
    })


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        otaHelper = OtaPresenter(this, requireActivity().applicationContext)
        AutoTestTaskManager.instance.otaPresenter = otaHelper
    }

    override fun onStart() {
        super.onStart()
        otaHelper?.start()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ota, container, false)
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        rv_file_list.layoutManager = LinearLayoutManager(activity)
        rv_file_list.addItemDecoration(CommonDecoration(activity, RecyclerView.VERTICAL))
        adapter = FileAdapter(arrayListOf())
        val emptyView = LayoutInflater.from(context).inflate(R.layout.view_file_empty, null)
        val tvTips = emptyView.findViewById<TextView>(R.id.tv_file_empty_tips)
        tvTips.movementMethod = ScrollingMovementMethod.getInstance()
        adapter?.setEmptyView(emptyView)
        rv_file_list.adapter = adapter
        adapter?.setOnItemClickListener { _, _, position ->
            adapter?.setSelectedIndex(position)
        }
        btn_upgrade.setOnClickListener {
            val path: String? = adapter?.getSelectedItem()?.path
            JL_Log.w(tag, "ota file path : $path")
            if (path == null) return@setOnClickListener
            if (checkFile(path)) {
                val autoTestOTA =
                    PreferencesHelper.getSharedPreferences(activity?.applicationContext)
                        .getBoolean(JL_Constant.KEY_AUTO_TEST_OTA, JL_Constant.AUTO_TEST_OTA)
                if (autoTestOTA) {//自动化测试OTA
                    if (!AutoTestTaskManager.instance.isAutoTesting()) {//正在自动化测试
                        otaHelper?.getConnectedDevice()?.let {
                            AutoTestTaskManager.instance.startAutoTest(it, path)
                            autoTestStatisticsUtil.clear()
                            autoTestStatisticsUtil.testSum = AutoTestTaskManager.AUTO_TEST_OTA_COUNT
                            updateAutoOTAProgressTv(
                                autoTestStatisticsUtil.currentCount,
                                autoTestStatisticsUtil.testSum,
                                View.VISIBLE
                            )
                        }
                    } else {
                        ToastUtil.showToastShort(getString(R.string.auto_test_ota_tip))
                    }
                } else {//普通的OTA
                    otaHelper?.startOTA(path)
                    updateAutoOTAProgressTv(
                        autoTestStatisticsUtil.currentCount,
                        autoTestStatisticsUtil.testSum,
                        View.GONE
                    )
                }
            } else {
                ToastUtil.showToastShort(getString(R.string.ota_please_chose_file))
            }
        }
        iv_refresh_file_list.setOnClickListener {
            readFileList()
        }

        updateDeviceConnectedStatus(otaHelper?.isDevConnected())
        OtaFileObserverHelper.getInstance().registerFileObserverCallback { _, path ->
            if (path != null) {
                mHandler.removeMessages(MSG_UPDATE_OTA_FILE_LIST)
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_OTA_FILE_LIST, 300)
            }
        }
//        readFileList()
    }

    override fun onResume() {
        super.onResume()
        readFileList()
    }

    override fun onDestroy() {
        super.onDestroy()
        otaHelper?.destroy()
    }

    private fun updateConnectedDeviceInfo(device: BluetoothDevice?) {
        if (isAdded && !isDetached) {
            if (device == null) {
                tv_connect_dev_name_vale.text = ""
                tv_connect_dev_address_vale.text = ""
                tv_connect_dev_type_vale.text = ""
            } else {
                tv_connect_dev_name_vale.text = device.name
                tv_connect_dev_address_vale.text = device.address
                tv_connect_dev_type_vale.text = getBtDeviceTypeString(device.type)
            }
        }
    }

    private fun getBtDeviceTypeString(type: Int?): String {
        if (type == null) return ""
        var typeName = ""
        when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> typeName =
                getString(R.string.device_type_classic)
            BluetoothDevice.DEVICE_TYPE_LE -> typeName = getString(R.string.device_type_ble)
            BluetoothDevice.DEVICE_TYPE_DUAL -> typeName = getString(R.string.device_type_dual_mode)
            BluetoothDevice.DEVICE_TYPE_UNKNOWN -> typeName =
                getString(R.string.device_type_unknown)
        }
        return typeName
    }

    private fun updateUpgradeTips(content: String?, visibility: Int) {
        if (isAdded && !isDetached) {
            tv_upgrade_tips?.visibility = visibility
            if (visibility == View.VISIBLE && content != null) {
                tv_upgrade_tips?.text = content
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUpgradeProgressTv(progress: Float, visibility: Int) {
        if (isAdded && !isDetached) {
            tv_upgrade_progress?.visibility = visibility
            if (visibility == View.VISIBLE) {
                val cProgress = progress.roundToInt()
                tv_upgrade_progress?.text = "$cProgress %"
            }
        }
    }

    private fun updateUpgradeProgressPb(progress: Float, visibility: Int) {
        if (isAdded && !isDetached) {
            bar_upgrade?.visibility = visibility
            if (visibility == View.VISIBLE) {
                val cProgress = progress.roundToInt()
                bar_upgrade?.progress = cProgress
            }
        }
    }

    private fun updateAutoOTAProgressTv(progress: Int, sum: Int, visibility: Int) {
        if (isAdded && !isDetached) {
            tv_auto_test_progress?.visibility = visibility
            if (visibility == View.VISIBLE) {
                tv_auto_test_progress?.text = getString(R.string.auto_test_ota) + ": $progress/$sum"
            }
        }
    }

    private fun checkFile(path: String): Boolean {
        JL_Log.i("sen", "checkFile-->$path")

        val file = File(path)

        if (!file.exists()) {
            return false
        }

        //todo 升级文件校验


        return true
    }

    //最多支持5个文件
    private fun readFileList() {

        val parentPath = FileUtil.splicingFilePath(
            activity,
            requireActivity().packageName,
            JL_Constant.DIR_UPGRADE,
            null,
            null
        )
        val parent = File(parentPath)
        var files: Array<File> = arrayOf()

        if (parent.exists()) {
            parent.listFiles()?.iterator()?.forEach {
                if (it.name.endsWith(".ufw") || it.name.endsWith(".bfu")) {
                    files = files.plus(it)
                }
            }
        } else {
            parent.mkdirs()
        }

        JL_Log.w(tag, "readFileList ---> ${files.size}, adapter = $adapter")

        adapter?.setNewInstance(mutableListOf())
        for (element in files) {
            adapter?.addData(element)
        }
    }

    private fun updateOtaUiStatus(isUpgrade: Boolean) {
        if (isUpgrade) {
            btn_upgrade.visibility = View.INVISIBLE
            bar_upgrade.visibility = View.VISIBLE
        } else {
            btn_upgrade.visibility = View.VISIBLE
            bar_upgrade.visibility = View.INVISIBLE
        }
    }


    override fun onDestroyView() {
        mHandler.removeCallbacksAndMessages(null)
        if (otaHelper!!.isOTA()) {
            otaHelper?.cancelOTA()
        }
        super.onDestroyView()
    }


    private fun updateDeviceConnectedStatus(connected: Boolean?) {
        var visibility = View.INVISIBLE
        if (connected!!) {
            visibility = View.VISIBLE
        }
        if (connected) {
            tv_connect_status.text = getString(R.string.device_status_connected)
            btn_upgrade.isEnabled = true
            btn_upgrade.visibility = visibility
            btn_upgrade.setBackgroundResource(R.drawable.bg_btn_upgrade)
            updateUpgradeTips(getString(R.string.ota_upgrade_not_started), visibility)
            updateUpgradeProgressTv(0.0f, View.GONE)
            updateUpgradeProgressPb(0.0f, visibility)
            updateConnectedDeviceInfo(otaHelper?.getConnectedDevice())
        } else {
            tv_connect_status.text = getString(R.string.device_status_disconnected)
            btn_upgrade.isEnabled = false
            btn_upgrade.visibility = View.VISIBLE
            btn_upgrade.setBackgroundResource(R.drawable.dbg_btn_unenable)
            updateUpgradeTips(null, View.VISIBLE)
            updateUpgradeProgressTv(0.0f, View.GONE)
            bar_upgrade.visibility = visibility
            updateConnectedDeviceInfo(null)
        }
    }

    override fun setPresenter(presenter: IOtaContract.IOtaPresenter) {
        otaHelper ?: presenter
    }

    override
    fun onOTAError(code: Int, message: String) {
        getAutoTestView()?.onOTAError(code, message)
        JL_Log.e(tag, "startOta has error : $message")
        mHandler.post {
            if (isAdded) {
                if (code == ErrorCode.SUB_ERR_OTA_IN_HANDLE) {
                    ToastUtil.showToastShort(message)
                    return@post
                } else if (code == ErrorCode.SUB_ERR_FILE_NOT_FOUND) {
                    readFileList()
                }
                val text = String.format(getString(R.string.ota_upgrade_failed), message)
                updateUpgradeProgressTv(0.0f, View.GONE)
                updateUpgradeTips(text, View.VISIBLE)
                updateOtaUiStatus(false)
            }
        }
    }

    override
    fun onOTAReconnect(btAddr: String?) {
        //TODO:实现自定义回连
        getAutoTestView()?.onOTAReconnect(btAddr)
        if (context != null && PreferencesHelper.getSharedPreferences(context).getBoolean(
                JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                JL_Constant.NEED_CUSTOM_RECONNECT_WAY
            )
        ) {
            otaHelper?.reconnectDev(btAddr)
        }
    }

    override fun isViewShow(): Boolean {
        return isVisible
    }

    override fun onConnection(device: BluetoothDevice?, status: Int) {
        getAutoTestView()?.onConnection(device, status)
        if (!otaHelper!!.isOTA()) {
            updateDeviceConnectedStatus(otaHelper?.isDevConnected())
        }
    }

    override fun onMandatoryUpgrade() {
        getAutoTestView()?.onMandatoryUpgrade()
        ToastUtil.showToastShort(R.string.device_must_mandatory_upgrade)
    }

    override
    fun onOTAStart() {
        autoTestStatisticsUtil.currentCount++
        getAutoTestView()?.onOTAStart()
        if (!isAdded) return
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateUpgradeProgressPb(0.0f, View.INVISIBLE)
        updateUpgradeProgressTv(0.0f, View.INVISIBLE)
        updateUpgradeTips(getString(R.string.ota_checking_upgrade_file), View.VISIBLE)
        updateOtaUiStatus(true)
        updateAutoOTAProgressTv(
            autoTestStatisticsUtil.currentCount,
            autoTestStatisticsUtil.testSum,
            View.VISIBLE
        )
    }

    override
    fun onOTAStop() {
        getAutoTestView()?.onOTAStop()
        mHandler.post {
            if (!isAdded) return@post
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            updateUpgradeProgressTv(0.0f, View.GONE)
            updateUpgradeTips(getString(R.string.ota_complete), View.VISIBLE)
//                    updateOtaUiStatus(false)
            updateDeviceConnectedStatus(false)
        }
    }

    override
    fun onOTACancel() {
        getAutoTestView()?.onOTACancel()
        if (!isAdded) return
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateUpgradeProgressTv(0.0f, View.GONE)
        updateUpgradeTips(getString(R.string.ota_upgrade_cancel), View.VISIBLE)
        updateOtaUiStatus(false)
    }

    override
    fun onOTAProgress(type: Int, progress: Float) {
        getAutoTestView()?.onOTAProgress(type, progress)
        if (!isAdded) return
        if (progress > 0) {
            updateUpgradeProgressTv(progress, View.VISIBLE)
            val message =
                if (type == 0) getString(R.string.ota_check_file) else getString(R.string.ota_upgrading)
            updateUpgradeTips(message, View.VISIBLE)
        } else {
            updateUpgradeProgressTv(0.0f, View.INVISIBLE)
        }
        updateUpgradeProgressPb(progress, View.VISIBLE)
    }

    private fun getAutoTestView(): IOtaContract.IOtaView? {
        AutoTestTaskManager.instance.currentTestTask?.let {
            return it as? IOtaContract.IOtaView
        }
        return null
    }
}


