package com.jieli.otasdk_autotest.fragments


import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.jieli.component.utils.ToastUtil
import com.jieli.jlFileTransfer.Constants
import com.jieli.jlFileTransfer.FileUtils
import com.jieli.jlFileTransfer.WifiUtils
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.util.JL_Log
import com.jieli.otasdk.MainApplication
import com.jieli.otasdk.R
import com.jieli.otasdk.activities.MainActivity
import com.jieli.otasdk.dialog.DialogFileTransfer
import com.jieli.otasdk.dialog.DialogFileTransferListener
import com.jieli.otasdk.dialog.DialogOTA
import com.jieli.otasdk.fragments.*
import com.jieli.otasdk.model.ota.OTAEnd
import com.jieli.otasdk.model.ota.OTAState
import com.jieli.otasdk.tool.config.ConfigHelper
import com.jieli.otasdk.util.AppUtil
import com.jieli.otasdk.util.FileTransferUtil
import com.jieli.otasdk.util.OtaFileObserverHelper
import com.jieli.otasdk.viewmodel.OTAViewModel
import com.jieli.otasdk_autotest.dialog.DialogOTAAutoTest
import com.jieli.otasdk_autotest.viewmodel.OTAAutoTestViewModel
import kotlinx.android.synthetic.main.dialog_add_file_operation.view.*
import kotlinx.android.synthetic.main.dialog_file_operation.view.*
import kotlinx.android.synthetic.main.fragment_ota.*
import java.io.File
import java.io.IOException


/**
 * 升级界面
 */
class OtaAutoTestFragment : BaseFileFragment() {
    private lateinit var filePicker: ActivityResultLauncher<String>
    private lateinit var otaViewModel: OTAAutoTestViewModel
    private lateinit var adapter: FileAdapter
    private var mIsFirstResume = true
    private var mDialogFileTransfer: DialogFileTransfer? = null;
    private var mIsHasStoragePermission = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mIsHasStoragePermission = AppUtil.isHasStoragePermission(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ota, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        otaViewModel = ViewModelProvider(requireActivity()).get(OTAAutoTestViewModel::class.java)
        initUI()
        observeCallback()
        filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                try {
                    activity?.contentResolver?.let { contentResolver ->
                        val parentFilePath = MainApplication.getOTAFileDir()
                        var fileName = FileUtils.getFileName(context, it)
                        fileName =
                            FileTransferUtil.getNewUpgradeFileName(fileName, File(parentFilePath))
                        val saveFileDialog = DialogInputText().run {
                            this.title = this@OtaAutoTestFragment.getString(R.string.save_file)
                            this.content = fileName
                            this.leftText = this@OtaAutoTestFragment.getString(R.string.cancel)
                            this.rightText = this@OtaAutoTestFragment.getString(R.string.save)
                            this.dialogClickListener = object : DialogClickListener {
                                override fun rightBtnClick(inputText: String?) {
                                    var inputFileNameStr: String = (inputText ?: "").trim()
                                    if (!inputFileNameStr.toUpperCase().endsWith(".UFW")) {
                                        ToastUtil.showToastShort(this@OtaAutoTestFragment.getString(R.string.ufw_format_file_tips))
                                        return
                                    }
                                    val resultPath =
                                        parentFilePath + File.separator + inputFileNameStr
                                    if (File(resultPath).exists()) {
                                        ToastUtil.showToastShort(this@OtaAutoTestFragment.getString(R.string.file_name_existed))
                                        return
                                    } else {
                                        FileUtils.copyFile(
                                            contentResolver.openInputStream(it),
                                            resultPath
                                        )
                                        Toast.makeText(
                                            context,
                                            R.string.please_refresh_web,
                                            Toast.LENGTH_LONG
                                        )
                                            .show()
                                    }
                                    this@run.dismiss()
                                }

                                override fun leftBtnClick(inputText: String?) {
                                    this@run.dismiss()
                                }
                            }
                            this
                        }
                        saveFileDialog.show(
                            this@OtaAutoTestFragment.parentFragmentManager,
                            "scanFilterDialog"
                        )
                    }
//                            RxBus.get().post(Constants.RxBusEventType.LOAD_BOOK_LIST, 0)
                } catch (e: IOException) {
                    Toast.makeText(context, R.string.read_file_failed, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        updateOTAConnectionUI(otaViewModel.isConnected(), otaViewModel.getConnectedDevice())
        otaViewModel.readFileList()
    }

    override fun onResume() {
        super.onResume()
        if (mIsHasStoragePermission != AppUtil.isHasStoragePermission(context)) {//存储权限发生变化
            mIsHasStoragePermission = AppUtil.isHasStoragePermission(context)
            val tempPath = MainApplication.getOTAFileDir()
            OtaFileObserverHelper.getInstance().updateObserverPath(tempPath)
        }

        if (mIsFirstResume) {
            mIsFirstResume = false
            checkExternalStorage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        otaViewModel.destroy()
    }

    private fun initUI() {
        rv_file_list.layoutManager = LinearLayoutManager(requireContext())
        adapter = FileAdapter(otaViewModel.isAutoTest(), mutableListOf())
        adapter.setOnItemClickListener { _, _, position ->
            adapter.setSelectedIndex(position)
        }
        adapter.setOnItemLongClickListener { adapter, view1, position ->
            val file: File = adapter.data.get(position) as File
            this.context?.run {
                val view = LayoutInflater.from(this).inflate(R.layout.dialog_file_operation, null)
                val popupWindow = PopupWindow(
                    view,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                popupWindow.isOutsideTouchable = true
                popupWindow.showAsDropDown(view1, 0, -100, Gravity.RIGHT or Gravity.BOTTOM)
                view.tv_delete_file.setOnClickListener {
                    if (file.exists()) {
                        file.delete()
                    }
                    popupWindow.dismiss()
                }
            }
            return@setOnItemLongClickListener false
        }
        val emptyView =
            LayoutInflater.from(requireContext()).inflate(R.layout.view_file_empty, null)
        adapter.setEmptyView(emptyView)
        rv_file_list.adapter = adapter

        ibtn_file_operation.setOnClickListener {
            checkExternalStorage(object : OnCheckExternalStorageEnvironmentCallback {
                override fun onSuccess() {
                    this@OtaAutoTestFragment.context?.run {
                        val view =
                            LayoutInflater.from(this)
                                .inflate(R.layout.dialog_add_file_operation, null)
                        val popupWindow = PopupWindow(
                            view,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT
                        )
                        popupWindow.isOutsideTouchable = true
                        popupWindow.showAsDropDown(it)
                        view.tv_upgrade_file_browse_local.setOnClickListener {
                            //  跳到本地文件浏览
                            filePicker.launch("application/octet-stream")
                            popupWindow.dismiss()
                        }
                        view.tv_upgrade_file_http_transfer.setOnClickListener {
                            //  传输升级文件 从电脑
                            popupWindow.dismiss()
                            mDialogFileTransfer = DialogFileTransfer().run {
                                val ipAddr: String = WifiUtils.getDeviceIpAddress()
                                val address = "http://$ipAddr:${Constants.HTTP_PORT}"
                                this.isCancelable = false
                                this.httpUrl = address
                                this.mListener = object : DialogFileTransferListener {
                                    override fun onLeftButtonClick() {
//                                WebService.stop(context)
                                        this@run.dismiss()
                                        mDialogFileTransfer = null
                                    }

                                    override fun onRightButtonClick() {
                                        val cm =
                                            context!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val mClipData = ClipData.newPlainText("Label", address)
                                        cm.setPrimaryClip(mClipData)
                                        Toast.makeText(
                                            context,
                                            context!!.getString(R.string.copy_toast),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                this.show(this@OtaAutoTestFragment.parentFragmentManager, "file_transfer")
                                this
                            }
                        }
                        view.tv_log_file_http_transfer.setOnClickListener {
                            // TODO: 传输Log文件到电脑
                            popupWindow.dismiss()
                        }
                    }

                }

                override fun onFailed() {
                }
            })
        }
        btn_upgrade.setOnClickListener {
            var testCount = if (otaViewModel.isAutoTest()) {
                ConfigHelper.getInstance().getAutoTestCount()
            } else 1
            var faultTolerantCount =
                if (otaViewModel.isAutoTest() && otaViewModel.isFaultTolerant()) {
                    ConfigHelper.getInstance().getFaultTolerantCount()
                } else 0
            val pathList = adapter.getSelectedItems()
            JL_Log.w(TAG, "ota file size : ${pathList.size}")
            if (pathList.isEmpty()) {
                ToastUtil.showToastShort(getString(R.string.ota_please_chose_file))
                return@setOnClickListener
            }
            if (otaViewModel.getDeviceInfo() == null) {
                ToastUtil.showToastShort(getString(R.string.bt_not_connect_device))
                return@setOnClickListener
            } else if (otaViewModel.isAutoTest() && otaViewModel.getDeviceInfo()!!.isMandatoryUpgrade) {
                //TODO: 多国语言翻译
                ToastUtil.showToastShort("强制升级设备不允许进行自动化测试")
                testCount = 1
            }
            if (otaViewModel.getDeviceInfo() == null) {
                ToastUtil.showToastShort(getString(R.string.bt_not_connect_device))
                return@setOnClickListener
            } else if (otaViewModel.isAutoTest() && otaViewModel.getDeviceInfo()!!.isMandatoryUpgrade) {
                //TODO: 多国语言翻译
                ToastUtil.showToastLong("强制升级设备不允许进行自动化测试,正在将设备先恢复正常")
                testCount = 1
                faultTolerantCount = 0
            }
            showOTADialog()
            otaViewModel.startOTA(testCount, faultTolerantCount, pathList)
        }
    }

    private fun checkExternalStorage(callback: OnCheckExternalStorageEnvironmentCallback? = null): Unit {
        checkExternalStorageEnvironment(object : OnCheckExternalStorageEnvironmentCallback {
            override fun onSuccess() {
                unregisterOnCheckBluetoothEnvironmentCallback(this)
                otaViewModel.readFileList()
                callback?.onSuccess()
            }

            override fun onFailed() {
                unregisterOnCheckBluetoothEnvironmentCallback(this)
                callback?.onFailed()
            }
        })
    }

    private fun observeCallback() {
        otaViewModel.fileListMLD.observe(viewLifecycleOwner) { fileList ->
            JL_Log.i(TAG, "readFileList ---> ${fileList.size}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fileList.sortWith { o1, o2 ->
                    if (o1.lastModified() > o2.lastModified()) {
                        -1
                    } else {
                        1
                    }
                }
            }
            adapter.onUpdateDataList(fileList)
            adapter.setNewInstance(fileList)
        }
        otaViewModel.otaConnectionMLD.observe(viewLifecycleOwner) { otaConnection ->
            requireActivity().run {
                JL_Log.d(TAG, "otaConnectionMLD : >>> $otaConnection")
                updateOTAConnectionUI(
                    otaConnection.state == StateCode.CONNECTION_OK,
                    otaConnection.device
                )
            }
        }
        otaViewModel.mandatoryUpgradeMLD.observe(viewLifecycleOwner) { device ->
            JL_Log.d(
                TAG, "mandatoryUpgradeMLD : >>> ${
                    AppUtil.printBtDeviceInfo(device)
                }"
            )
            (requireActivity() as MainActivity).switchSubFragment(1)
            ToastUtil.showToastShort(R.string.device_must_mandatory_upgrade)
        }
        otaViewModel.otaStateMLD.observe(viewLifecycleOwner) { otaState ->
            JL_Log.d(TAG, "otaStateMLD : >>> $otaState")
            if (otaState == null) return@observe
            requireActivity().run {
                when (otaState.state) {
                    OTAState.OTA_STATE_IDLE -> { //OTA结束
                        val otaEnd = otaState as OTAEnd
                        updateConnectedDeviceInfo(otaEnd.device)
                    }
                }
            }
        }
    }


    private fun updateOTABtn(isConnected: Boolean) {
        btn_upgrade?.let {
            it.isEnabled = isConnected
            it.visibility = if (otaViewModel.isOTA()) {
                View.INVISIBLE
            } else {
                View.VISIBLE
            }
            it.setBackgroundResource(
                if (isConnected) {
                    R.drawable.bg_btn_upgrade
                } else {
                    R.drawable.dbg_btn_unenable
                }
            )
        }
    }

    private fun updateOTAConnectionUI(isConnected: Boolean, device: BluetoothDevice?) {
        if (!isValidFragment()) return
        updateOTABtn(isConnected)
        updateConnectedDeviceInfo(device)
    }

    private fun updateConnectedDeviceInfo(device: BluetoothDevice?) {
        if (!isValidFragment()) return
        val isConnectedDevice = otaViewModel.isDeviceConnected(device)
        tv_connect_dev_name_vale?.let {
            it.text = if (isConnectedDevice) {
                AppUtil.getDeviceName(requireContext(), device)
            } else {
                ""
            }
        }
        tv_connect_dev_address_vale?.let {
            it.text = if (isConnectedDevice) {
                device!!.address
            } else {
                ""
            }
        }
        tv_connect_dev_type_vale?.let {
            it.text = if (isConnectedDevice) {
                getBtDeviceTypeString(AppUtil.getDeviceType(requireContext(), device))
            } else {
                ""
            }
        }
        tv_connect_status?.let {
            it.text = if (isConnectedDevice) {
                getString(R.string.device_status_connected)
            } else {
                getString(R.string.device_status_disconnected)
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

    private fun showOTADialog(): Unit {
        DialogOTAAutoTest().run {
            this.isCancelable = false
            this.show(this@OtaAutoTestFragment.parentFragmentManager, "file_transfer")
            this
        }
    }
}