package com.jieli.otasdk;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import com.jieli.jl_bt_ota.constant.ErrorCode;
import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.impl.BluetoothOTAManager;
import com.jieli.jl_bt_ota.interfaces.BtEventCallback;
import com.jieli.jl_bt_ota.interfaces.IActionCallback;
import com.jieli.jl_bt_ota.interfaces.IUpgradeCallback;
import com.jieli.jl_bt_ota.model.BluetoothOTAConfigure;
import com.jieli.jl_bt_ota.model.base.BaseError;
import com.jieli.jl_bt_ota.model.response.TargetInfoResponse;
import com.jieli.otasdk.tool.ota.ble.BleManager;
import com.jieli.otasdk.tool.ota.ble.interfaces.BleEventCallback;
import com.jieli.otasdk.tool.ota.ble.interfaces.OnWriteDataCallback;

import org.junit.Test;

import java.util.UUID;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc
 * @since 2022/2/7
 */
public class OtaDemo {

    @Test
    public void initOTA(Context context, String firmwarePath) {
        OtaManager otaManager = new OtaManager();
        BluetoothOTAConfigure bluetoothOption = BluetoothOTAConfigure.createDefault();
        bluetoothOption.setPriority(BluetoothOTAConfigure.PREFER_BLE) //请按照项目需要选择
                .setUseAuthDevice(true) //具体根据固件的配置选择
                .setBleIntervalMs(500) //默认是500毫秒
                .setTimeoutMs(3000) //超时时间
                .setMtu(514) //BLE底层通讯MTU值，会影响BLE传输数据的速率。建议用512。该MTU值会使OTA库在BLE连接时改变MTU，所以用户SDK需要对此处理。
                .setNeedChangeMtu(false); //不需要调整MTU，建议客户连接时调整好BLE的MTU
        bluetoothOption.setFirmwareFilePath(firmwarePath); //设置本地存储OTA文件的路径
//        bluetoothOption.setFirmwareFileData(firmwareData);//设置本地存储OTA文件的数据
        otaManager.configure(bluetoothOption); //设置OTA参数
    }

    @Test
    public void OtaFirmware(final String filePath) {
        //1.构建OTAManager对象
        OtaManager otaManager = new OtaManager();
        //2.注册事件监听器
        otaManager.registerBluetoothCallback(new BtEventCallback() {
            @Override
            public void onConnection(BluetoothDevice device, int status) {
                //必须等待库回调连接成功才可以开始OTA操作
                if (status == StateCode.CONNECTION_OK) {
                    //1.可以查询是否需要强制升级
                    otaManager.queryMandatoryUpdate(new IActionCallback<TargetInfoResponse>() {
                        @Override
                        public void onSuccess(TargetInfoResponse targetInfoResponse) {
                            /*
                             * TODO:说明设备需要强制升级，请跳转到OTA界面，引导用户升级固件
                             */
                            targetInfoResponse.getVersionCode(); //设备版本号
                            targetInfoResponse.getVersionName();  //设备版本名
                            targetInfoResponse.getProjectCode(); //设备产品ID(默认是0，如果设备支持会改变)
                        }

                        @Override
                        public void onError(BaseError baseError) {
                            /*
                             *可以不用处理，也可以获取设备信息
                             */
                            if (baseError.getCode() == ErrorCode.ERR_NONE && baseError.getSubCode() == ErrorCode.ERR_NONE){//没有错误，可以获取设备信息
                                TargetInfoResponse deviceInfo = otaManager.getDeviceInfo();
                                deviceInfo.getVersionCode(); //设备版本号
                                deviceInfo.getVersionName();  //设备版本名
                                deviceInfo.getProjectCode(); //设备产品ID(默认是0，如果设备支持会改变)
                            }
                        }
                    });
                    //2.也可以直接进行OTA升级
                    //* 需要先设置升级文件路径 - filePath
                    otaManager.getBluetoothOption().setFirmwareFilePath(filePath);
                    //* 进行OTA升级，然后根据回调进行UI更新
                    otaManager.startOTA(new IUpgradeCallback() {
                        @Override
                        public void onStartOTA() {
                            //回调开始OTA
                        }

                        @Override
                        public void onNeedReconnect(String addr) {
                            //回调需要回连的设备地址
                        }

                        @Override
                        public void onProgress(int type, float progress) {
                            //回调OTA进度
                            //type : 0 --- 下载uboot  1 --- 升级固件
                        }

                        @Override
                        public void onStopOTA() {
                            //回调OTA升级完成
                        }

                        @Override
                        public void onCancelOTA() {
                            //回调OTA升级被取消
                        }

                        @Override
                        public void onError(BaseError error) {
                            //回调OTA升级发生的错误事件
                        }
                    });
                }
            }
        });
        //...
        //3.OTA操作完成后，需要注销事件监听器和释放资源
//        otaManager.unregisterBluetoothCallback(this);
        otaManager.release();
    }
}

class OtaManager extends BluetoothOTAManager {
    private final BleManager bleManager = BleManager.getInstance();

    public OtaManager() {
        super(MainApplication.getInstance().getApplicationContext());
        bleManager.registerBleEventCallback(new BleEventCallback() {
            @Override
            public void onBleConnection(BluetoothDevice device, int status) {
                super.onBleConnection(device, status);
                int connectStatus = changeConnectStatus(status); //注意：转变成OTA库的连接状态
                //传递设备的连接状态
                onBtDeviceConnection(device, connectStatus);
            }

            @Override
            public void onBleDataNotification(BluetoothDevice device, UUID serviceUuid, UUID characteristicsUuid, byte[] data) {
                super.onBleDataNotification(device, serviceUuid, characteristicsUuid, data);
                //传递设备的接收数据
                onReceiveDeviceData(device, data);
            }

            @Override
            public void onBleDataBlockChanged(BluetoothDevice device, int block, int status) {
                super.onBleDataBlockChanged(device, block, status);
                //传递BLE的MTU改变
                onMtuChanged(getConnectedBluetoothGatt(), block, status);
            }
        });
    }

    @Override
    public BluetoothDevice getConnectedDevice() {
        return bleManager.getConnectedBtDevice();
    }

    @Override
    public BluetoothGatt getConnectedBluetoothGatt() {
        return bleManager.getConnectedBtGatt();
    }

    @Override
    public void connectBluetoothDevice(BluetoothDevice device) {
        bleManager.connectBleDevice(device);
    }

    @Override
    public void disconnectBluetoothDevice(BluetoothDevice device) {
        bleManager.disconnectBleDevice(device);
    }

    @Override
    public boolean sendDataToDevice(BluetoothDevice device, byte[] data) {
        bleManager.writeDataByBleAsync(device, BleManager.BLE_UUID_SERVICE, BleManager.BLE_UUID_WRITE, data, new OnWriteDataCallback() {
            @Override
            public void onBleResult(BluetoothDevice device, UUID serviceUUID, UUID characteristicUUID, boolean result, byte[] data) {
                //返回结果
            }
        });
        //也可以阻塞等待结果
        return true;
    }

    @Override
    public void release() {
        super.release();
        bleManager.destroy();
    }

    private int changeConnectStatus(int status) {
        int changeStatus = StateCode.CONNECTION_DISCONNECT;
        switch (status) {
            case BluetoothProfile.STATE_DISCONNECTED:
            case BluetoothProfile.STATE_DISCONNECTING: {
                changeStatus = StateCode.CONNECTION_DISCONNECT;
                break;
            }
            case BluetoothProfile.STATE_CONNECTED:
                changeStatus = StateCode.CONNECTION_OK;
                break;
            case BluetoothProfile.STATE_CONNECTING:
                changeStatus = StateCode.CONNECTION_CONNECTING;
                break;
        }
        return changeStatus;
    }
}
