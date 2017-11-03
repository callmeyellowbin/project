package com.example.lijinduo.mydemo.view;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.LruCache;
import android.webkit.MimeTypeMap;

import com.jakewharton.disklrucache.DiskLruCache;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
import com.tencent.smtt.sdk.WebView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import ren.yale.android.cachewebviewlib.StaticRes;
import ren.yale.android.cachewebviewlib.bean.HttpCacheFlag;
import ren.yale.android.cachewebviewlib.utils.AppUtils;
import ren.yale.android.cachewebviewlib.utils.MD5Utils;


/**
 * 版权：XXX公司 版权所有
 * 作者：lijinduo
 * 版本：2.0
 * 创建日期：2017/10/31
 * 描述：(重构)
 * 修订历史：
 * 参考链接：
 */
public class WebViewCache {

    private DiskLruCache mDiskLruCache;
    private StaticRes mStaticRes;
    private HashMap<String,Map> mHeaderMaps;

    private Context mContext;
    private File mCacheFile;
    private long mCacheSize;
    private long mCacheRamSize;

    private LruCache<String,RamObject> mLruCache;

    private static class InstanceHolder {
        public static final WebViewCache INSTANCE = new WebViewCache();
    }
    private WebViewCache(){
        mStaticRes = new StaticRes();
    }
    public StaticRes getStaticRes(){
        return mStaticRes;
    }
    public static WebViewCache getInstance(){
        return WebViewCache.InstanceHolder.INSTANCE;
    }


    public WebViewCache openCache(Context context, File directory, long maxSize) throws IOException {

        return openCache(context,directory,maxSize,maxSize/10);
    }
    public WebViewCache openCache(Context context, File directory, long maxDiskSize, long maxRamSize) throws IOException {

        if (mContext==null){
            mContext = context.getApplicationContext();
        }
        if (mCacheFile==null){
            mCacheFile = directory;
        }
        if (mCacheSize<=0){
            mCacheSize = maxDiskSize;
        }
        if (mCacheRamSize<=0){
            mCacheRamSize = maxRamSize;
        }
        if (mHeaderMaps==null){
            mHeaderMaps = new HashMap<>();
        }
        if (mDiskLruCache==null){
            mDiskLruCache = DiskLruCache.open(mCacheFile, AppUtils.getVersionCode(mContext),2,mCacheSize);
        }
        ensureLruCache();
        return this;
    }
    private void ensureLruCache(){
        if(mLruCache==null){
            synchronized (ren.yale.android.cachewebviewlib.WebViewCache.class){
                if (mLruCache == null){
                    mLruCache = new LruCache<String,RamObject>((int) mCacheRamSize){
                        @Override
                        protected int sizeOf(String key, RamObject value) {

                            return value.getInputStreamSize()+value.getHttpFlag().getBytes().length;
                        }
                    };
                }
            }
        }
    }

    /**
     * Create DiskLruCache
     * @param directory a writable directory
     * @param maxDiskSize the maximum number of bytes this cache should use to store
     * @throws IOException if reading or writing the cache directory fails
     */
    public WebViewCache init(Context context, File directory, long maxDiskSize){

        mContext = context.getApplicationContext();
        mCacheFile = directory;
        mCacheSize = maxDiskSize;
        mCacheRamSize =maxDiskSize/10;
        return this;
    }
    /**
     * Create DiskLruCache
     * @param directory a writable directory
     * @param maxDiskSize the maximum number of bytes this cache should use to store
     * @throws IOException if reading or writing the cache directory fails
     */
    public WebViewCache init(Context context, File directory, long maxDiskSize, long maxRamSize){

        mContext = context.getApplicationContext();
        mCacheFile = directory;
        mCacheSize = maxDiskSize;
        mCacheRamSize =maxRamSize;
        return this;
    }
    /**
     * Create DiskLruCache
     * @param directory a writable directory
     * @throws IOException if reading or writing the cache directory fails
     */
    public WebViewCache init(Context context, File directory){
        return  init(context,directory,Integer.MAX_VALUE,20*1024*1024);
    }
    public void clean(){
        if (mDiskLruCache!=null){
            try {
                FileUtil.deleteDirs(mDiskLruCache.getDirectory().getAbsolutePath(),true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mLruCache!=null){
            Map<String,RamObject> map = (LinkedHashMap) mLruCache.snapshot();
            if (map!=null){
                map.clear();
            }
            mLruCache = null;
        }
    }

    public static String getKey(String url){
        return MD5Utils.getMD5(url,false);
    }
    private DiskLruCache.Editor getEditor(String key){
        try {
            DiskLruCache.Editor editor = mDiskLruCache.edit(key);
            return editor;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    public void addHeaderMap(String url, Map<String, String> additionalHttpHeaders){
        if(mHeaderMaps!=null&&additionalHttpHeaders!=null){
            mHeaderMaps.put(url,additionalHttpHeaders);
        }
    }
    public void clearHeaderMap(HashMap<String,Map> map){
        if(mHeaderMaps!=null&&map!=null){
            for (Map.Entry entry : map.entrySet()){
                mHeaderMaps.remove(entry.getKey());
            }
        }
    }

    public InputStream httpRequest(WebView view, String url) {

        try {
            URL urlRequest = new URL(url);

            HttpURLConnection httpURLConnection = (HttpURLConnection) urlRequest.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setUseCaches(false);
            httpURLConnection.setConnectTimeout(30000);
            httpURLConnection.setReadTimeout(30000);

            Map<String,Object> header = mHeaderMaps.get(url);
            if (header!=null){
                for (Map.Entry entry: header.entrySet()){
                    httpURLConnection.setRequestProperty((String)entry.getKey(),(String)entry.getValue());
                }
            }
            HttpCacheFlag local = getCacheFlag(url);
            if (local!=null){
                if (!TextUtils.isEmpty(local.getLastModified())){
                    httpURLConnection.setRequestProperty("If-Modified-Since",local.getLastModified());
                }
                if (!TextUtils.isEmpty(local.getEtag())){
                    httpURLConnection.setRequestProperty("If-None-Match",local.getEtag());
                }
            }

            CacheWebView cacheWebView = (CacheWebView) view;
            if (cacheWebView!=null){
                httpURLConnection.setRequestProperty("Origin",cacheWebView.getOriginUrl());
                httpURLConnection.setRequestProperty("Referer",cacheWebView.getRefererUrl());
                httpURLConnection.setRequestProperty("User-Agent",cacheWebView.getUserAgent());
            }

            httpURLConnection.connect();
            HttpCache remote = new HttpCache(httpURLConnection);

            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK){

                return new ResourseInputStream(url,httpURLConnection.getInputStream(),
                        getEditor(getKey(url)),remote,mLruCache);
            }
            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED){
                InputStream  inputStream = getCacheInputStream(url);
                ResourseInputStream resourseInputStream = null;
                if (inputStream == null){
                    resourseInputStream = new ResourseInputStream(url,httpURLConnection.getInputStream(),
                            getEditor(getKey(url)),remote,mLruCache);
                }else{
                    CacheWebViewLog.d(url+" 304 from cache");
                    resourseInputStream = new ResourseInputStream(url,inputStream,
                            null,remote,mLruCache);
                }
                return resourseInputStream;
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }

        return null;
    }

    private HttpCacheFlag getCacheFlag(String url){

        HttpCacheFlag  httpCacheFlag = getRamCacheFlag(url);
        if (httpCacheFlag!=null){
            return httpCacheFlag;
        }
        InputStream inputStream = null;
        try {
            DiskLruCache.Snapshot snapshot=  mDiskLruCache.get(getKey(url));
            if (snapshot!=null){
                inputStream =  snapshot.getInputStream(CacheIndexType.PROPERTY.ordinal());
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                StringBuffer sb = new StringBuffer();
                byte buffer[] = new byte[1024];
                int len = 0;
                while ((len = bufferedInputStream.read(buffer,0,1024))>0){
                    sb.append(new String(buffer,0,len));
                }
                bufferedInputStream.close();
                return new JsonWrapper(sb.toString()).getBean(HttpCacheFlag.class);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    private HttpCacheFlag getRamCacheFlag(String url){
        RamObject ramObject = mLruCache.get(getKey(url));
        if (ramObject!=null&&!TextUtils.isEmpty(ramObject.getHttpFlag())){
            try {
                return new JsonWrapper(ramObject.getHttpFlag()).getBean(HttpCacheFlag.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    private InputStream getRamCache(String url){
        RamObject ramObject = mLruCache.get(getKey(url));
        if (ramObject!=null){
            InputStream inputStream =  ramObject.getStream();
            if (inputStream!=null){
                try {
                    inputStream.reset();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return inputStream;
        }
        return null;
    }

    private InputStream getCacheInputStream(String url){
        InputStream inputStream = getRamCache(url);
        if (inputStream!=null){
            CacheWebViewLog.d(url +": from ram cache");
            return inputStream;
        }
        try {
            DiskLruCache.Snapshot snapshot=  mDiskLruCache.get(getKey(url));
            if (snapshot!=null){
                inputStream =  snapshot.getInputStream(CacheIndexType.CONTENT.ordinal());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (inputStream!=null){
            CacheWebViewLog.d(url +": from disk cache");
        }
        return inputStream;
    }
    public WebResourceResponse getWebResourceResponse(WebView view, String url){

        return getWebResourceResponse(view,url,WebViewCache.CacheStrategy.NORMAL);
    }
    public WebResourceResponse getWebResourceResponse(WebView view,String url, WebViewCache.CacheStrategy cacheStrategy){

        if(mDiskLruCache==null){
            return null;
        }

        if (TextUtils.isEmpty(url)){
            return null;
        }
        if (!url.startsWith("http")){
            return null;
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(url.toLowerCase());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

        if (TextUtils.isEmpty(extension)){
            return null;
        }
        if (!mStaticRes.canCache(extension)){
            return null;
        }
        InputStream inputStream = null;

        if (extension.equals("html")||extension.equals("htm")){
            cacheStrategy = WebViewCache.CacheStrategy.NORMAL;
        }

        ensureLruCache();

        if (NetworkUtils.isConnected(mContext)){

            if(cacheStrategy == WebViewCache.CacheStrategy.NORMAL){
                HttpCacheFlag httpCacheFlag = getCacheFlag(url);
                if (httpCacheFlag!=null&&!httpCacheFlag.isLocalOutDate()){
                    inputStream = getCacheInputStream(url);
                }
            }else if (cacheStrategy == WebViewCache.CacheStrategy.FORCE){
                inputStream = getCacheInputStream(url);
            }

        }else{
            inputStream = getCacheInputStream(url);
        }
        if (inputStream==null){
            inputStream = httpRequest(view,url);
        }

        if (inputStream !=null){
            if (inputStream instanceof ResourseInputStream){

                ResourseInputStream resourseInputStream= (ResourseInputStream) inputStream;
                WebResourceResponse webResourceResponse=   new WebResourceResponse(mimeType,"utf-8",inputStream);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    webResourceResponse.setResponseHeaders(resourseInputStream.getHttpCache().getResponseHeader());
                }
                return webResourceResponse;
            }else{
                return new WebResourceResponse(mimeType,"utf-8",inputStream);
            }

        }

        return null;

    }

    public enum CacheStrategy{
        NORMAL,FORCE
    }

}
