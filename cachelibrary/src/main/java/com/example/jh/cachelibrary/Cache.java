package com.example.jh.cachelibrary;

import android.content.Context;

/**
 * Created by Fussen on 2017/12/4.
 * <p>
 * 缓存工具类
 */

public class Cache {


    public static CacheManager with(Context context) {
        Bulder bulder = Bulder.get();
        return bulder.get(context);
    }


}
