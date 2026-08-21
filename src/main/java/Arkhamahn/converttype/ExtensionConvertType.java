package Arkhamahn.converttype;

import org.parosproxy.paros.Constant;
import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;

/**
 * ZAP extension that adds the "Convert Content Type" popup menu.
 *
 * <p>Ported from the Burp Suite extension
 * <a href="https://github.com/h0tak88r/Convert-Type-Convert-All">Convert-Type-Convert-All</a>.
 */
public class ExtensionConvertType extends ExtensionAdaptor {

    public static final String NAME = "ExtensionConvertType";

    private static final String PREFIX = "converttype";

    public ExtensionConvertType() {
        super(NAME);
        setI18nPrefix(PREFIX);
    }

    @Override
    public void hook(ExtensionHook extensionHook) {
        super.hook(extensionHook);

        if (hasView()) {
            for (ContentType target : ContentType.values()) {
                extensionHook
                        .getHookMenu()
                        .addPopupMenuItem(new ConvertTypePopupMenuItem(target));
            }
        }
    }

    @Override
    public boolean canUnload() {
        return true;
    }

    @Override
    public String getDescription() {
        return Constant.messages.getString(PREFIX + ".desc");
    }

    @Override
    public String getAuthor() {
        return Constant.messages.getString(PREFIX + ".author");
    }
}