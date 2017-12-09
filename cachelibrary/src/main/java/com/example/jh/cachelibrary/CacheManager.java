package com.example.jh.cachelibrary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.example.jh.cachelibrary.Util.getAppVersion;
import static com.example.jh.cachelibrary.Util.getCacheDir;
import static com.example.jh.cachelibrary.Util.requireNonNull;


/**
 * Created by Fussen on 2017/12/4.
 */

public class CacheManager {


    private DiskLruCache mDiskLruCache;

    private String path;

    private Context context;

    private int maxSize = 20;//unit :m

    private Set<SaveImageTask> taskCollection;


    public CacheManager(Context context) {
        this.context = context;
        path = getCacheDir(context);

        if (taskCollection == null) {
            taskCollection = new HashSet<>();
        }
        initCache(context);
    }



    private void initCache(Context context) {

        try {
            File cacheDir = new File(path);

            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            if (mDiskLruCache == null || (mDiskLruCache != null && !mDiskLruCache.getDirectory().getPath().toString().equals(path))) {

                if (mDiskLruCache != null) {
                    mDiskLruCache.close();
                }
                mDiskLruCache = DiskLruCache.open(cacheDir, getAppVersion(context), 1, maxSize * 1024 * 1024);

                System.out.println("......create DiskLruCache......");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * set cache path
     *
     * @param path
     * @return
     */
    public CacheManager setPath(String path) {
        this.path = path;
        initCache(context);
        return this;
    }




    /**
     * save cache
     *
     * @param key
     * @param object
     */
    public boolean saveCache(String key, Object object) {


        requireNonNull(object, "cache data can't be null");

        try {
            DiskLruCache.Editor edit = mDiskLruCache.edit(getKey(key));

            OutputStream fos = edit.newOutputStream(0);

            if (writeToStream(fos, object)) {
                edit.commit();
            } else {
                edit.abort();
            }
            mDiskLruCache.flush();

            return true;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }


    /**
     * save image cache
     *
     * @param imageUrl
     */
    public void saveImage(String imageUrl) {

        try {
            SaveImageTask saveImageTask = new SaveImageTask();
            taskCollection.add(saveImageTask);
            saveImageTask.execute(imageUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * read cache
     *
     * @param key
     * @param cls
     * @param <T>
     * @return
     */
    public <T> T getCache(String key, Class<T> cls) {


        try {
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(getKey(key));

            if (snapshot != null) {
                InputStream inputStream = snapshot.getInputStream(0);
                return readStream(inputStream, cls);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * get image cache by image url
     *
     * @param imageUrl
     * @return
     */
    public Bitmap getImageCache(String imageUrl) {
        try {
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(getKey(imageUrl));
            if (snapshot != null) {
                InputStream inputStream = snapshot.getInputStream(0);
                return BitmapFactory.decodeStream(inputStream);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * read list cache
     *
     * @param key
     * @return
     */
    public <T> List<T> getCacheList(String key, Class<T> cls) {


        try {
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(getKey(key));

            if (snapshot != null) {
                InputStream inputStream = snapshot.getInputStream(0);
                return readListStream(inputStream, cls);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }


    /**
     * close cache
     */
    public void closeCache() {
        if (mDiskLruCache == null) {
            return;
        }
        try {
            if (taskCollection != null) {
                for (SaveImageTask task : taskCollection) {
                    task.cancel(false);
                }
                taskCollection.clear();
            }
            mDiskLruCache.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * delete cache by key
     *
     * @param key
     * @return
     */
    public boolean remove(String key) {
        if (mDiskLruCache == null) {
            return false;
        }

        try {
            return mDiskLruCache.remove(key);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private <T> List<T> readListStream(InputStream inputStream, Class<T> cls) {

        System.out.println("...... className : " + cls.getSimpleName() + " ......");

        List<T> resultList = new ArrayList<>();
        ObjectInputStream ois = null;

        try {
            ois = new ObjectInputStream(inputStream);
            ArrayList<T> list_ext = (ArrayList<T>) ois.readObject();

            for (T obj : list_ext) {
                if (obj != null) {
                    resultList.add(obj);
                }
            }
            return resultList;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        return resultList;
    }


    private <T> T readStream(InputStream inputStream, Class<T> cls) {

        System.out.println("...... className : " + cls.getSimpleName() + " ......");

        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(inputStream);
            T object = (T) ois.readObject();
            return object;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }

        return null;
    }

    private boolean writeToStream(OutputStream fos, Object object) {

        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(fos);
            oos.writeObject(object);
            oos.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return false;
    }


    /**
     * url to diskStream
     *
     * @param imageUrl
     * @param outputStream
     * @return
     */
    private boolean writeUrlToStream(String imageUrl, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(imageUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
            out = new BufferedOutputStream(outputStream, 8 * 1024);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    /**
     * save image task
     */
    class SaveImageTask extends AsyncTask<String, Void, Boolean> {


        @Override
        protected Boolean doInBackground(String... params) {
            String imageUrl = params[0];

            try {

                DiskLruCache.Editor edit = mDiskLruCache.edit(getKey(imageUrl));

                OutputStream fos = edit.newOutputStream(0);

                if (writeUrlToStream(imageUrl, fos)) {
                    edit.commit();
                } else {
                    edit.abort();
                }
                mDiskLruCache.flush();
                return true;

            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean isFinish) {
            super.onPostExecute(isFinish);
            taskCollection.remove(this);
        }
    }

    /**
     * get key
     *
     * @param key :key
     * @return
     */
    private String getKey(String key) {

        requireNonNull(key, "key can't be null");

        String fileName = "cache_" + key;
        return MD5.encodeKey(fileName);
    }
}
