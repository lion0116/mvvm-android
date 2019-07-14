package com.mylive.live.component;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 使用说明：
 * 1、
 * 2、
 * 注：利用注解实现防混淆，不需添加防混淆规则
 * <p>
 * Created by Developer Zailong Shi on 2019-07-09.
 */
public class JsBridgeWebViewClient extends WebViewClient {

    private static final String TAG = JsBridgeWebViewClient.class.getSimpleName();
    private static final String JS_BRIDGE = "jsBridge";
    private Handler handler = new Handler(Looper.getMainLooper());
    private String invokeMethodName, onReturnMethodName;
    private Object localApi;
    private Map<String, Method> localApiMap;
    private Map<String, Callback> callbackMap;
    private WeakReference<WebView> view;
    private String jsBridge;

    {
        Method[] methods = getClass().getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(JsBridgeApi.class)) {
                if ("invoke".equals(method.getAnnotation(JsBridgeApi.class).value())) {
                    invokeMethodName = method.getName();
                } else if ("onReturn".equals(method.getAnnotation(JsBridgeApi.class).value())) {
                    onReturnMethodName = method.getName();
                }
                if (invokeMethodName != null && onReturnMethodName != null) {
                    break;
                }
            }
        }
    }

    public JsBridgeWebViewClient(WebView view) {
        this(view, JS_BRIDGE);
    }

    public JsBridgeWebViewClient(WebView view, String name) {
        Objects.requireNonNull(view);
        Objects.requireNonNull(name);
        this.jsBridge = name;
        if (this.view == null || this.view.get() != view) {
            this.view = new WeakReference<>(view);
        }
        view.getSettings().setJavaScriptEnabled(true);
        view.addJavascriptInterface(this, jsBridge);
    }

    @Override
    public final void onPageStarted(WebView view, String url, Bitmap favicon) {
        injectBridgeApi();
        injectCustomApi();
        super.onPageStarted(view, url, favicon);
        onPageStarted(view, this, url, favicon);
    }

    public void onPageStarted(WebView view, JsBridgeWebViewClient client,
                              String url, Bitmap favicon) {
    }

    private void injectBridgeApi() {
        String proxy = invokeMethodName != null && !"invoke".equals(invokeMethodName) ?
                ("window.$jsBridge.invoke = function(name, params) {"
                        + "window.$jsBridge.$invoke(name, params);"
                        + "};")
                        .replace("$invoke", invokeMethodName)
                : "";
        proxy += onReturnMethodName != null && !"onReturn".equals(onReturnMethodName) ?
                ("window.$jsBridge.onReturn = function(name, params) {"
                        + "window.$jsBridge.$onReturn(name, params);"
                        + "};")
                        .replace("$onReturn", onReturnMethodName)
                : "";
        String injectScript = proxy
                + "window.$jsBridge.callbacks = {};"
                + "window.$jsBridge.callback = function(callbackId, args) {"
                + "window.eval('window.$jsBridge.callbacks[\"' + callbackId + '\"](' + String(args) + ')');"
                + "};"
                + "window.$jsBridge.call = function(name, args) {"
                + "window.eval('window.$jsBridge.' + name + '(' + String(args) + ')');"
                + "};"
                + "window.$jsBridge.error = function(error) {"
                + "console.error('JsBridge error: ' + error);"
                + "};";
        evaluateJavascript(injectScript.replace("$jsBridge", jsBridge));
    }

    private void injectCustomApi() {
        if (localApiMap == null) {
            throw new IllegalStateException("请在页面开始加载前设置JsBridgeApi对象。");
        }
        StringBuilder injectScript = new StringBuilder();
        for (Map.Entry<String, Method> entry : localApiMap.entrySet()) {
            String name = entry.getKey();
            Class<?>[] paramTypes = entry.getValue().getParameterTypes();
            StringBuilder saveCallbacks = new StringBuilder();
            String[] args = new String[paramTypes.length];
            int argIndex = 1, callbackIndex = 1;
            for (int i = 0; i < paramTypes.length; i++) {
                if (Callback.class.isAssignableFrom(paramTypes[i])) {
                    args[i] = "callback$i".replace("$i", String.valueOf(callbackIndex++));
                    saveCallbacks.append("window.$jsBridge.callbacks['$callbackId']=$callback;"
                            .replace("$jsBridge", jsBridge)
                            .replace("$callbackId", callbackId(name, args[i]))
                            .replace("$callback", args[i]));
                } else {
                    args[i] = "arg$i".replace("$i", String.valueOf(argIndex++));
                }
            }
            StringBuilder argsStr = new StringBuilder();
            StringBuilder argsStrArr = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                argsStr.append(args[i]);
                if (Callback.class.isAssignableFrom(paramTypes[i])) {
                    argsStrArr.append("'$arg'".replace("$arg", args[i]));
                } else {
                    argsStrArr.append("String($arg)".replace("$arg", args[i]));
                }
                if (i < args.length - 1) {
                    argsStr.append(",");
                    argsStrArr.append(",");
                }
            }
            injectScript.append(
                    ("window.$jsBridge.$name = function($args) {"
                            + "$save_callbacks"
                            + "return eval(window.$jsBridge.invoke('$name', [$argArray]));"
                            + "};")
                            .replace("$jsBridge", jsBridge)
                            .replace("$name", name)
                            .replace("$args", argsStr)
                            .replace("$save_callbacks", saveCallbacks)
                            .replace("$argArray", argsStrArr)
            );
        }
        evaluateJavascript(injectScript.toString());
    }

    public <T> T createRemoteApi(Class<T> remoteApi) {
        Objects.requireNonNull(remoteApi);
        if (!remoteApi.isInterface()) {
            throw new IllegalArgumentException("定义远程api必须用接口类型。");
        }
        callbackMap = new HashMap<>();
        //noinspection unchecked
        return (T) Proxy.newProxyInstance(
                remoteApi.getClassLoader(),
                new Class[]{remoteApi},
                (proxy, method, args) -> {
                    String name = getMethodName(method);
                    if (!void.class.isAssignableFrom(method.getReturnType())) {
                        throw new IllegalStateException(name + "方法有返回值，"
                                + "调用Javascript函数不支持同步获取返回值，只可使用异步回调。");
                    }
                    if (localApiMap.containsKey(name)) {
                        throw new IllegalStateException("Javascript函数" + name
                                + "与本地方法冲突。");
                    }
                    call(name, args);
                    return null;
                });
    }

    public void addLocalApi(Object localApi) {
        Objects.requireNonNull(localApi);
        this.localApi = localApi;
        if (localApiMap == null) {
            localApiMap = new HashMap<>();
        }
        localApiMap.clear();
        Method[] methods = this.localApi.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getModifiers() == Modifier.PUBLIC
                    || method.isAnnotationPresent(JsBridgeApi.class)) {
                String name = getMethodName(method);
                localApiMap.put(name, method);
            }
        }
    }

    @JsBridgeApi("invoke")
    @JavascriptInterface
    public String invoke(String name, String... params) {
        Method method = localApiMap.get(name);
        if (method != null) {
            try {
                Object[] args = handleParams(method, name, params);
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                if (!void.class.isAssignableFrom(method.getReturnType())
                        && !Void.class.isAssignableFrom(method.getReturnType())) {
                    return convert(method.invoke(localApi, args));
                } else {
                    method.invoke(localApi, args);
                }
            } catch (Exception e) {
                log(e);
            }
        } else {
            log("JsBridge Exception: 找不到名为" + name + "的方法(函数)。");
        }
        return null;
    }

    @JsBridgeApi("onReturn")
    @JavascriptInterface
    public void onReturn(String callbackName, String... params) {
        Callback callback = callbackMap.get(callbackName);
        if (callback != null) {
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                args[i] = convert(params[i]);
            }
            callback.call(args);
        }
    }

    private void call(String function, Object... args) {
        String callJsFunc = String.format("window.$jsBridge.call('%s', %s);"
                        .replace("$jsBridge", jsBridge),
                function, construct(args));
        evaluateJavascript(callJsFunc);
    }

    private void callback(String callbackId, Object... args) {
        String callCallbackFunc = String.format("window.$jsBridge.callback('%s', %s);"
                        .replace("$jsBridge", jsBridge),
                callbackId, construct(args));
        evaluateJavascript(callCallbackFunc);
    }

    private String callbackId(String functionName, String callbackName) {
        return functionName + "#" + callbackName;
    }

    private String getMethodName(Method method) {
        return method.isAnnotationPresent(JsBridgeApi.class) ?
                method.getAnnotation(JsBridgeApi.class).value()
                : method.getName();
    }

    private void error(String error) {
        if (error != null && error.contains("\n")) {
            error = error.replace("\n", "'\n+'");
        }
        String callErrorFunc = String.format("window.$jsBridge.error('%s');"
                .replace("$jsBridge", jsBridge), error);
        evaluateJavascript(callErrorFunc);
    }

    private void log(Throwable e) {
        log(collectExceptionMessage(e));
    }

    private void log(String message) {
        Log.e(TAG, message);
        error(message);
    }

    private void evaluateJavascript(String javascript) {
        if (TextUtils.isEmpty(javascript)) {
            return;
        }
        WebView view = this.view.get();
        if (view == null) {
            return;
        }
        if (!javascript.startsWith("javascript:")) {
            javascript = "javascript:" + javascript;
        }
        String finalJavascript = javascript;
        handler.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                view.evaluateJavascript(finalJavascript, value -> {
                });
            } else {
                view.loadUrl(finalJavascript);
            }
        });
    }

    private Object[] handleParams(Method method, String name, String... params)
            throws JSONException, InstantiationException, IllegalAccessException {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; params != null && i < params.length && i < args.length; i++) {
            String param = params[i];
            Class<?> paramType = paramTypes[i];
            if (String.class.isAssignableFrom(paramType)) {
                args[i] = param;
            } else if (int.class.isAssignableFrom(paramType)
                    || Integer.class.isAssignableFrom(paramType)) {
                args[i] = Integer.valueOf(param);
            } else if (long.class.isAssignableFrom(paramType)
                    || Long.class.isAssignableFrom(paramType)) {
                args[i] = Long.valueOf(param);
            } else if (float.class.isAssignableFrom(paramType)
                    || Float.class.isAssignableFrom(paramType)) {
                args[i] = Float.valueOf(param);
            } else if (double.class.isAssignableFrom(paramType)
                    || Double.class.isAssignableFrom(paramType)) {
                args[i] = Double.valueOf(param);
            } else if (boolean.class.isAssignableFrom(paramType)
                    || Boolean.class.isAssignableFrom(paramType)) {
                args[i] = Boolean.valueOf(param);
            } else if (JSONObject.class.isAssignableFrom(paramType)) {
                args[i] = new JSONObject(param);
            } else if (Callback.class.isAssignableFrom(paramType)) {
                args[i] = (Callback) objectArgs -> {
                    callback(callbackId(name, param), objectArgs);
                };
            } else {
                JSONObject jsonObject = new JSONObject(param);
                Object object = paramType.newInstance();
                Field[] fields = object.getClass().getDeclaredFields();
                for (Field field : fields) {
                    String key = field.isAnnotationPresent(JsBridgeField.class) ?
                            field.getAnnotation(JsBridgeField.class).value()
                            : field.getName();
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    field.set(object, jsonObject.get(key));
                }
            }
        }
        return args;
    }

    private String convert(Object object)
            throws IllegalAccessException, JSONException {
        if (object instanceof Integer
                || object instanceof Long
                || object instanceof Float
                || object instanceof Double
                || object instanceof Boolean
                || object instanceof JSONArray) {
            return object.toString();
        } else if (object instanceof String) {
            return "'" + object.toString() + "'";
        } else if (object instanceof Object[]) {
            return toJSONArray((Object[]) object).toString();
        } else if (object instanceof Callback) {
            String name = object.toString();
            callbackMap.put(name, (Callback) object);
            return ("function() {"
                    + "var args = Array.prototype.slice.apply(arguments);"
                    + "for (var i = 0; i < args.length; i++) {"
                    + "    var arg = args[i];"
                    + "    switch (arg.constructor) {"
                    + "        case String:"
                    + "             args[i] = \"'\" + String(arg) + \"'\";"
                    + "             break;"
                    + "        case Array:"
                    + "        case Object:"
                    + "             args[i] = JSON.stringify(arg);"
                    + "             break;"
                    + "        default:"
                    + "             args[i] = String(arg);"
                    + "             break;"
                    + "   }"
                    + "};"
                    + "window.$jsBridge.onReturn('$name', args);"
                    + "}")
                    .replace("$jsBridge", jsBridge)
                    .replace("$name", name);
        } else if (object instanceof JSONObject) {
            return "(" + object.toString() + ")";
        } else if (object != null) {
            JSONObject jsonObject = new JSONObject();
            Field[] fields = object.getClass().getDeclaredFields();
            for (Field field : fields) {
                String key = field.isAnnotationPresent(JsBridgeField.class) ?
                        field.getAnnotation(JsBridgeField.class).value()
                        : field.getName();
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                jsonObject.put(key, field.get(object));
            }
            return "(" + object.toString() + ")";
        }
        return null;
    }

    private Object convert(String text) {
        if (!TextUtils.isEmpty(text)) {
            if (text.startsWith("'") && text.endsWith("'")) {
                return text.replace("'", "");
            } else if (text.startsWith("[") && text.endsWith("]")) {
                try {
                    return new JSONArray(text);
                } catch (JSONException e) {
                    log(e);
                }
            } else if (text.startsWith("{") && text.endsWith("}")) {
                try {
                    return new JSONObject(text);
                } catch (JSONException e) {
                    log(e);
                }
            }
        }
        return text;
    }

    private JSONArray toJSONArray(Object[] objects) {
        JSONArray jsonArray = new JSONArray();
        for (Object o : objects) {
            if (o instanceof Object[]) {
                //解决内部数组对象不会自动转成JSON数组的问题
                jsonArray.put(toJSONArray((Object[]) o));
            } else {
                jsonArray.put(o);
            }
        }
        return jsonArray;
    }

    private String construct(Object[] args) {
        StringBuilder argsStr = new StringBuilder("[");
        for (Object arg : args) {
            String converted = null;
            try {
                converted = convert(arg);
            } catch (IllegalAccessException e) {
                log(e);
            } catch (JSONException e) {
                log(e);
            }
            /* 为了js数组转字符串后根节点下的字符串、
             * JSON对象(包括数组)不被直接转成js对象，
             * 即保留字符串类型，在首尾增加引号来实现 */
            if (arg instanceof JSONObject
                    || arg instanceof Object[]) {
                converted = "'" + converted + "'";
            } else if (arg instanceof String) {
                converted = "\"" + converted + "\"";
            }
            argsStr.append(converted).append(",");
        }
        int index = argsStr.lastIndexOf(",");
        if (index != -1) {
            argsStr.delete(index, index + ",".length());
        }
        argsStr.append("]");
        return argsStr.toString();
    }

    private String collectExceptionMessage(Throwable e) {
        StringBuilder message = new StringBuilder();
        collectExceptionMessage(message, e);
        return message.toString();
    }

    private void collectExceptionMessage(StringBuilder message, Throwable e) {
        message.append(e.toString()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement stackTraceElement : e.getStackTrace()) {
            message.append("at ").append(stackTraceElement.toString()).append("\n");
        }
        if (e.getCause() != null && e.getCause() != e) {
            collectExceptionMessage(message, e.getCause());
        }
    }

    public interface Callback {
        void call(Object... args);
    }

    public interface Callback2<T> {
        void call(T t);
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface JsBridgeApi {
        String value();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface JsBridgeField {
        String value();
    }
}
