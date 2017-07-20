package com.liuwei1995.plug.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by liuwei on 2017/7/20 10:00
 */

public class HookStartActivityUtils {

    private Context mContext;
    private String mProxyClassName;

    public HookStartActivityUtils(Context context, String proxyClassName) {
        if (context == null)throw new NullPointerException("HookStartActivityUtils  context == null");
        this.mContext = context.getApplicationContext() == null ? context : context.getApplicationContext();
        this.mProxyClassName = proxyClassName;
    }

    public void hookLaunchActivity() throws Exception {
//        获取ActivityThread 实例
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Field scatField = atClass.getDeclaredField("sCurrentActivityThread");
        scatField.setAccessible(true);
        Object sCurrentActivityThread = scatField.get(null);


//        获取 获取ActivityThread 里面的mH    Handler
        Field mHField = atClass.getDeclaredField("mH");
        mHField.setAccessible(true);
        Object mH = mHField.get(sCurrentActivityThread);


//给handler设置callback
        Field mCallbackField = Handler.class.getDeclaredField("mCallback");
        mCallbackField.setAccessible(true);
        mCallbackField.set(mH,new HandlerCallback());
    }

    public class HandlerCallback implements Handler.Callback{

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 100){
                handlerLaunchActivity(msg);
            }
            return false;
        }

        private void handlerLaunchActivity(Message msg) {
            try {
//            ActivityClientRecord
                Object record = msg.obj;
                Field intentField = record.getClass().getDeclaredField("intent");
                intentField.setAccessible(true);

                Intent safeIntent = (Intent) intentField.get(record);
//                取出最开始的 orignIntent    StartActivityInvocationHandler 里面设置的
                Intent orignIntent = safeIntent.getParcelableExtra(EXTRA_ORIGN_INTENT);
                if (orignIntent != null){
                    intentField.set(record,orignIntent);
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }


    public void hookStartActivity() throws Exception {
        //获得ActivityManagerNative
        Class<?> amnClass = Class.forName("android.app.ActivityManagerNative");
        //获得ActivityManagerNative.getDefault静态方法
        Field gDefaultField = amnClass.getDeclaredField("gDefault");
//         private static final
        gDefaultField.setAccessible(true);
//        static  获取  Singleton 对象
        Object gDefault = gDefaultField.get(null);


        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);


        //获取 真正的 IActivityManager 实例
        Object iamInstance = mInstanceField.get(gDefault);


        Class<?> iamClass = Class.forName("android.app.IActivityManager");
//        获取自己的IActivityManager代理对象
        iamInstance = Proxy.newProxyInstance(HookStartActivityUtils.class.getClassLoader(), new Class[]{iamClass}, new StartActivityInvocationHandler(iamInstance));

//替换ActivityManagerNative 里面的 IActivityManager 为自己的IActivityManager代理对象 即 iamInstance对象
        mInstanceField.set(gDefault,iamInstance);
    }

    private static final String TAG = "HookStartActivityUtils";

    public static final String EXTRA_ORIGN_INTENT = "EXTRA_ORIGN_INTENT";

    private class StartActivityInvocationHandler implements InvocationHandler{

        private Object mObject;

        public StartActivityInvocationHandler(Object mObject) {
            this.mObject = mObject;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("startActivity")){
                Intent intent = (Intent) args[2];
//                创建一个安全的intent
                Intent safeIntent = new Intent();
                safeIntent.setComponent(new ComponentName(mContext,mProxyClassName));
                safeIntent.putExtra(EXTRA_ORIGN_INTENT,intent);
                args[2] = safeIntent;
                Log.e(TAG,"startActivity=="+method.getName());
            }else {
                Log.e(TAG,"invoke=="+method.getName());
            }
            return method.invoke(mObject,args);
        }
    }


}
