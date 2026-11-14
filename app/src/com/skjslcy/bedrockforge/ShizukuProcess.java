package com.skjslcy.bedrockforge;

import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Process wrapper around Shizuku's IShizukuService.newProcess.
 */
public final class ShizukuProcess extends Process {

    private final IRemoteProcess remote;
    private final OutputStream out;
    private final InputStream in;
    private final InputStream err;

    private ShizukuProcess(IRemoteProcess remote) throws Exception {
        this.remote = remote;
        ParcelFileDescriptor o = remote.getOutputStream();
        ParcelFileDescriptor i = remote.getInputStream();
        ParcelFileDescriptor e = remote.getErrorStream();
        this.out = new ParcelFileDescriptor.AutoCloseOutputStream(o);
        this.in = new ParcelFileDescriptor.AutoCloseInputStream(i);
        this.err = new ParcelFileDescriptor.AutoCloseInputStream(e);
    }

    public static ShizukuProcess start(String[] cmd, String[] env, String dir) throws Exception {
        if (!Shizuku.pingBinder()) throw new IOException(I18n.t("Shizuku binder 未就绪"));
        IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
        if (service == null) throw new IOException(I18n.t("Shizuku service 为空"));
        IRemoteProcess rp = service.newProcess(cmd, env, dir);
        if (rp == null) throw new IOException(I18n.t("Shizuku newProcess 返回空"));
        return new ShizukuProcess(rp);
    }

    @Override
    public OutputStream getOutputStream() {
        return out;
    }

    @Override
    public InputStream getInputStream() {
        return in;
    }

    @Override
    public InputStream getErrorStream() {
        return err;
    }

    @Override
    public int waitFor() {
        try {
            return remote.waitFor();
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public int exitValue() {
        try {
            return remote.exitValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public void destroy() {
        try {
            remote.destroy();
        } catch (Throwable ignored) {
        }
    }

    public boolean alive() {
        try {
            return remote.alive();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean waitForTimeout(long timeout, String unit) {
        try {
            return remote.waitForTimeout(timeout, unit);
        } catch (Throwable t) {
            return false;
        }
    }
}
