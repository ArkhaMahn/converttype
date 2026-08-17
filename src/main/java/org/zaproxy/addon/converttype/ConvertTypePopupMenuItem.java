package org.zaproxy.addon.converttype;

import java.awt.Component;
import java.util.List;
import javax.swing.SwingUtilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.extension.history.ExtensionHistory;
import org.parosproxy.paros.extension.manualrequest.ManualRequestEditorDialog;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.extension.httppanel.HttpPanel;
import org.zaproxy.zap.view.messagecontainer.http.HttpMessageContainer;
import org.zaproxy.zap.view.popup.PopupMenuItemHttpMessageContainer;

/**
 * Popup menu item that converts the selected request to a {@link ContentType}.
 *
 * <p>The items are grouped under a "Convert Content Type" submenu. When the invoker is a message
 * editor (Request/Response tab), the converted request is applied in place and the editor view is
 * refreshed; otherwise the converted request is opened in the built-in Resend dialog.
 */
@SuppressWarnings("serial")
public class ConvertTypePopupMenuItem extends PopupMenuItemHttpMessageContainer {

    private static final long serialVersionUID = 1L;

    /** Slightly above the built-in "Open/Resend with Requester" item (25050). */
    private static final int PARENT_WEIGHT = 25100;

    private static final Logger LOGGER = LogManager.getLogger(ConvertTypePopupMenuItem.class);

    private final ContentType target;

    public ConvertTypePopupMenuItem(ContentType target) {
        super(Constant.messages.getString("converttype.popup.to." + target.getKey()));
        this.target = target;
    }

    @Override
    public boolean isSubMenu() {
        return true;
    }

    @Override
    public String getParentMenuName() {
        return Constant.messages.getString("converttype.popup.parent");
    }

    @Override
    public int getParentWeight() {
        return PARENT_WEIGHT;
    }

    @Override
    public int getWeight() {
        return target.ordinal();
    }

    @Override
    public boolean isSafe() {
        return true;
    }

    @Override
    public boolean isEnableForInvoker(Invoker invoker, HttpMessageContainer httpMessageContainer) {
        return true;
    }

    @Override
    protected void performActions(HttpMessageContainer httpMessageContainer) {
        List<HttpMessage> messages = getSelectedMessages(httpMessageContainer);
        if (messages.isEmpty()) {
            return;
        }

        for (HttpMessage message : messages) {
            try {
                ConversionResult result = RequestConverter.convert(message, target);
                RequestConverter.apply(message, result);
            } catch (Exception e) {
                LOGGER.warn("Unable to convert request to {}: {}", target, e.getMessage());
                View.getSingleton()
                        .showWarningDialog(
                                Constant.messages.getString(
                                        "converttype.popup.error", target.getKey(), e.getMessage()));
                return;
            }
        }

        if (httpMessageContainer instanceof HttpPanel
                && ((HttpPanel) httpMessageContainer).getMessage() != null) {
            ((HttpPanel) httpMessageContainer)
                    .setMessage(((HttpPanel) httpMessageContainer).getMessage());
            return;
        }

        HttpPanel panel =
                (HttpPanel) SwingUtilities.getAncestorOfClass(HttpPanel.class, httpMessageContainer.getComponent());
        if (panel != null && panel.getMessage() != null) {
            panel.setMessage(panel.getMessage());
            return;
        }

        openInResendDialog(messages.get(messages.size() - 1));
    }

    @Override
    protected void performAction(HttpMessage message) {
        // Unused: performActions(HttpMessageContainer) is overridden.
    }

    private void openInResendDialog(HttpMessage message) {
        if (!View.isInitialised()) {
            return;
        }
        ExtensionHistory extensionHistory =
                Control.getSingleton()
                        .getExtensionLoader()
                        .getExtension(ExtensionHistory.class);
        if (extensionHistory == null) {
            return;
        }
        ManualRequestEditorDialog dialog = extensionHistory.getResendDialog();
        dialog.setMessage(message.cloneRequest());
        dialog.setVisible(true);
    }
}