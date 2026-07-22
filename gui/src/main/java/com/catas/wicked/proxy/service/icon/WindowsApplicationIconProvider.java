package com.catas.wicked.proxy.service.icon;

import com.catas.wicked.common.bean.ProcessInfo;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class WindowsApplicationIconProvider implements ApplicationIconProvider {

    private static final int SHGFI_ICON = 0x00000100;
    private static final int SHGFI_LARGEICON = 0x00000000;
    private static final int BI_RGB = 0;
    private static final int DIB_RGB_COLORS = 0;

    @Override
    public Optional<ApplicationIconData> load(ProcessInfo info) {
        String executablePath = StringUtils.firstNonBlank(
                info.getApplicationExecutablePath(), info.getOwnerExecutablePath());
        if (StringUtils.isBlank(executablePath) || !Files.isRegularFile(Path.of(executablePath))) {
            return Optional.empty();
        }
        ShellFileInfo fileInfo = new ShellFileInfo();
        BaseTSD.ULONG_PTR result = ShellIconLibrary.INSTANCE.SHGetFileInfo(
                new WString(executablePath), 0, fileInfo, fileInfo.size(), SHGFI_ICON | SHGFI_LARGEICON);
        fileInfo.read();
        if (result == null || result.longValue() == 0 || fileInfo.hIcon == null) {
            return Optional.empty();
        }
        try {
            return toImage(fileInfo.hIcon);
        } finally {
            User32.INSTANCE.DestroyIcon(fileInfo.hIcon);
        }
    }

    private Optional<ApplicationIconData> toImage(WinDef.HICON icon) {
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        if (!User32.INSTANCE.GetIconInfo(icon, iconInfo)) {
            return Optional.empty();
        }
        iconInfo.read();
        try {
            if (iconInfo.hbmColor == null) {
                return Optional.empty();
            }
            WinGDI.BITMAP bitmap = new WinGDI.BITMAP();
            if (GDI32.INSTANCE.GetObject(iconInfo.hbmColor, bitmap.size(), bitmap.getPointer()) == 0) {
                return Optional.empty();
            }
            bitmap.read();
            int width = bitmap.bmWidth.intValue();
            int height = Math.abs(bitmap.bmHeight.intValue());
            if (width <= 0 || height <= 0) {
                return Optional.empty();
            }

            WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
            bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size();
            bitmapInfo.bmiHeader.biWidth = width;
            bitmapInfo.bmiHeader.biHeight = -height;
            bitmapInfo.bmiHeader.biPlanes = 1;
            bitmapInfo.bmiHeader.biBitCount = 32;
            bitmapInfo.bmiHeader.biCompression = BI_RGB;
            int stride = width * 4;
            Memory pixels = new Memory((long) stride * height);
            WinDef.HDC deviceContext = User32.INSTANCE.GetDC(null);
            try {
                int lines = GDI32.INSTANCE.GetDIBits(deviceContext, iconInfo.hbmColor, 0, height,
                        pixels, bitmapInfo, DIB_RGB_COLORS);
                if (lines == 0) {
                    return Optional.empty();
                }
            } finally {
                User32.INSTANCE.ReleaseDC(null, deviceContext);
            }
            byte[] bgra = pixels.getByteArray(0, stride * height);
            repairMissingAlpha(bgra);
            return Optional.of(new ApplicationIconData.Bgra(width, height, bgra));
        } finally {
            deleteBitmap(iconInfo.hbmColor);
            deleteBitmap(iconInfo.hbmMask);
        }
    }

    static void repairMissingAlpha(byte[] bgra) {
        boolean hasAlpha = false;
        for (int i = 3; i < bgra.length; i += 4) {
            if (bgra[i] != 0) {
                hasAlpha = true;
                break;
            }
        }
        if (!hasAlpha) {
            for (int i = 3; i < bgra.length; i += 4) {
                bgra[i] = (byte) 0xff;
            }
        }
    }

    private static void deleteBitmap(WinDef.HBITMAP bitmap) {
        if (bitmap != null) {
            GDI32.INSTANCE.DeleteObject(bitmap);
        }
    }

    interface ShellIconLibrary extends StdCallLibrary {
        ShellIconLibrary INSTANCE = Native.load("shell32", ShellIconLibrary.class, W32APIOptions.UNICODE_OPTIONS);

        BaseTSD.ULONG_PTR SHGetFileInfo(WString path, int fileAttributes, ShellFileInfo fileInfo,
                                       int fileInfoSize, int flags);
    }

    @Structure.FieldOrder({"hIcon", "iIcon", "dwAttributes", "szDisplayName", "szTypeName"})
    public static class ShellFileInfo extends Structure {
        public WinDef.HICON hIcon;
        public int iIcon;
        public WinDef.DWORD dwAttributes;
        public char[] szDisplayName = new char[260];
        public char[] szTypeName = new char[80];
    }
}
