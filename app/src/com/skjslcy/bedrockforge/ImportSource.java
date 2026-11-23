package com.skjslcy.bedrockforge;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 用户从设备里选中的一个文件。
 *
 * <p>优先保留 ContentResolver 的读取流：导入时直接灌进设备，不再先落 App 缓存
 * （旧实现要拷两遍，257 MB 的部署包等于多走 514 MB I/O）。
 * 只有在「页面状态丢失」这类需要本地文件的场景才 materialize() 落盘。
 */
final class ImportSource {

    private final ContentResolver resolver;
    private final Uri uri;
    private final File file;
    final String name;
    final long size;

    private ImportSource(ContentResolver resolver, Uri uri, File file, String name, long size) {
        this.resolver = resolver;
        this.uri = uri;
        this.file = file;
        this.name = name == null ? "" : name;
        this.size = size;
    }

    static ImportSource ofUri(ContentResolver resolver, Uri uri, String name, long size) {
        return new ImportSource(resolver, uri, null, name, size);
    }

    static ImportSource ofFile(File file) {
        return new ImportSource(null, null, file, file.getName(), file.length());
    }

    long length() {
        return size > 0 ? size : (file != null ? file.length() : 0);
    }

    /** 把内容写进设备上的 remote 路径，字节级回调进度。 */
    void copyTo(String remote, ShizukuShell.CopyProgress cb) throws Exception {
        if (uri != null) {
            ShizukuShell.copyInStream(resolver, uri, size, remote, cb);
        } else {
            ShizukuShell.copyIn(file, remote, cb);
        }
    }

    /** 落一份缓存副本（仅隔离/补偿场景使用）。 */
    File materialize(Context ctx) throws Exception {
        if (file != null && file.exists()) return file;
        if (uri == null) throw new Exception(I18n.t("无法读取文件"));
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : ".bin";
        File out = new File(ctx.getCacheDir(), "import_" + System.currentTimeMillis() + ext);
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new Exception(I18n.t("无法读取文件"));
        FileOutputStream fos = new FileOutputStream(out);
        try {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        } finally {
            try { fos.close(); } catch (Throwable ignored) { }
            try { in.close(); } catch (Throwable ignored) { }
        }
        return out;
    }
}
