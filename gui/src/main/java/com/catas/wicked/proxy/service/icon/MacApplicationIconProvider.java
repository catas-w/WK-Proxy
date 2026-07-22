package com.catas.wicked.proxy.service.icon;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.jna.FoundationLibrary;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

final class MacApplicationIconProvider implements ApplicationIconProvider {

    private static final NativeLong PNG_FILE_TYPE = new NativeLong(4);

    @Override
    public Optional<ApplicationIconData> load(ProcessInfo info) {
        String executablePath = StringUtils.firstNonBlank(
                info.getApplicationExecutablePath(), info.getOwnerExecutablePath());
        String iconPath = appBundlePath(executablePath);
        if (iconPath == null || !Files.exists(Path.of(iconPath))) {
            return Optional.empty();
        }

        AppKitLibrary.ensureLoaded();
        FoundationLibrary objectiveC = FoundationLibrary.INSTANCE;
        NativeLong pool = send(objectiveC.objc_getClass("NSAutoreleasePool"), "new");
        NativeLong nativePath = FoundationLibrary.fromJavaString(iconPath);
        try {
            NativeLong workspace = send(objectiveC.objc_getClass("NSWorkspace"), "sharedWorkspace");
            NativeLong icon = send(workspace, "iconForFile:", nativePath);
            NativeLong tiff = send(icon, "TIFFRepresentation");
            NativeLong bitmap = send(objectiveC.objc_getClass("NSBitmapImageRep"), "imageRepWithData:", tiff);
            NativeLong properties = send(objectiveC.objc_getClass("NSDictionary"), "dictionary");
            NativeLong png = send(bitmap, "representationUsingType:properties:", PNG_FILE_TYPE, properties);
            if (isNull(png)) {
                return Optional.empty();
            }
            int length = Math.toIntExact(send(png, "length").longValue());
            NativeLong bytesAddress = send(png, "bytes");
            if (length <= 0 || isNull(bytesAddress)) {
                return Optional.empty();
            }
            byte[] bytes = new Pointer(bytesAddress.longValue()).getByteArray(0, length);
            return Optional.of(new ApplicationIconData.Png(bytes));
        } finally {
            send(nativePath, "release");
            send(pool, "drain");
        }
    }

    static String appBundlePath(String executablePath) {
        if (StringUtils.isBlank(executablePath)) {
            return null;
        }
        String normalized = executablePath.replace('\\', '/');
        int marker = normalized.toLowerCase(java.util.Locale.ROOT).indexOf(".app/contents/");
        if (marker >= 0) {
            return normalized.substring(0, marker + ".app".length());
        }
        return normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".app") ? normalized : null;
    }

    private static NativeLong send(NativeLong receiver, String selector, NativeLong... arguments) {
        if (isNull(receiver)) {
            return FoundationLibrary.NULL;
        }
        Pointer nativeSelector = FoundationLibrary.INSTANCE.sel_registerName(selector);
        return switch (arguments.length) {
            case 0 -> FoundationLibrary.INSTANCE.objc_msgSend(receiver, nativeSelector);
            case 1 -> FoundationLibrary.INSTANCE.objc_msgSend(receiver, nativeSelector, arguments[0]);
            case 2 -> FoundationLibrary.INSTANCE.objc_msgSend(receiver, nativeSelector, arguments[0], arguments[1]);
            default -> throw new IllegalArgumentException("Unsupported Objective-C argument count: " + arguments.length);
        };
    }

    private static boolean isNull(NativeLong value) {
        return value == null || value.longValue() == 0L;
    }

    private interface AppKitLibrary extends Library {
        AppKitLibrary INSTANCE = Native.load("AppKit", AppKitLibrary.class,
                Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));

        static void ensureLoaded() {
            INSTANCE.hashCode();
        }
    }
}
