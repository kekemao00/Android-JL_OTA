package com.jieli.otasdk.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.fragment.app.DialogFragment
import com.blankj.utilcode.util.ScreenUtils
import com.jieli.component.utils.HandlerManager
import com.jieli.jl_dialog.Jl_Dialog
import com.jieli.otasdk.R
import com.jieli.otasdk.base.BaseActivity
import com.jieli.otasdk.util.OtaFileObserverHelper
import permissions.dispatcher.*


@RuntimePermissions
class WelcomeActivity : BaseActivity() {
    private var notifyGpsDialog: Jl_Dialog? = null

    companion object {
        const val GPS_REQUEST_CODE = 1234
    }

    @NeedsPermission(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    fun toMainActivity() {
        if (checkGpsProviderEnable(applicationContext)) {
            goToMainActivity()
        } else {
            showNotifyGPSDialog()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenUtils.setFullScreen(this)
        setContentView(R.layout.activity_welcome)
        if (!isTaskRoot) {//是不是任务栈的第一个任务
            toMainActivityWithPermissionCheck() //申请权限
        } else {//显示welcome欢迎界面
            HandlerManager.getInstance().mainHandler.postDelayed(
                {
                    toMainActivityWithPermissionCheck() //申请权限
                },
                1000
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissNotifyGPSDialog()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(GPS_REQUEST_CODE == requestCode){
            goToMainActivity()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @OnShowRationale(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    fun showRationaleForPermission(request: PermissionRequest) {
        request.proceed()
    }

    @OnNeverAskAgain(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    fun onPermissionsNeverAskAgain() {
        //  Toast.makeText(this, "权限不再询问", Toast.LENGTH_SHORT).show()
        goToMainActivity()
    }

    @OnPermissionDenied(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    fun onPermissionsDenied(){
        goToMainActivity()
    }


    /**
     * 检查GPS位置功能是否使能
     *
     * @param context 上下文
     * @return 结果
     */
    private fun checkGpsProviderEnable(context: Context?): Boolean {
        if (context == null) return false
        val locManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * 显示打开定位服务(gps)提示框
     */
    private fun showNotifyGPSDialog() {
        if (isDestroyed || isFinishing) return
        if (notifyGpsDialog == null) {
            notifyGpsDialog = Jl_Dialog.Builder()
                .title(getString(R.string.tips))
                .content(getString(R.string.open_gpg_tip))
                .cancel(false)
                .left(getString(R.string.cancel))
                .leftColor(resources.getColor(R.color.gray_text_444444))
                .right(getString(R.string.to_setting))
                .rightColor(resources.getColor(R.color.red_FF688C))
                .leftClickListener { v: View?, dialogFragment: DialogFragment? ->
                    dismissNotifyGPSDialog()
                    goToMainActivity()
                }
                .rightClickListener { v: View?, dialogFragment: DialogFragment? ->
                    dismissNotifyGPSDialog()
                    startActivityForResult(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        GPS_REQUEST_CODE
                    )
                }
                .build()
        }
        if (!this.notifyGpsDialog?.isShow!!) {
            this.notifyGpsDialog?.show(supportFragmentManager, "notify_gps_dialog")
        }
    }

    private fun dismissNotifyGPSDialog() {
        if (notifyGpsDialog != null) {
            if (notifyGpsDialog!!.isShow && !isDestroyed) {
                notifyGpsDialog!!.dismiss()
            }
            notifyGpsDialog = null
        }
    }

    private fun goToMainActivity() {
        OtaFileObserverHelper.getInstance().startObserver()
        if (isTaskRoot) {//是不是任务栈的第一个任务
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
