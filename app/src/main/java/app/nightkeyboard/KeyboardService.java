package app.nightkeyboard;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class KeyboardService extends InputMethodService implements KeyboardView.Listener {
    private KeyboardView view;

    @Override
    public View onCreateInputView() {
        Cfg.firstRun(this);
        view = new KeyboardView(this);
        view.setListener(this);
        return view;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (view == null) return;
        view.reloadTheme();
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        boolean symbolsFirst = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE;
        view.setSymbols(symbolsFirst);
    }

    @Override
    public void onText(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    @Override
    public void onKey(int code) {
        if (code == KeyboardView.DEL) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        } else if (code == KeyboardView.ENTER) {
            EditorInfo ei = getCurrentInputEditorInfo();
            int action = ei == null ? EditorInfo.IME_ACTION_NONE : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noAction = ei == null
                    || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                    || action == EditorInfo.IME_ACTION_NONE
                    || action == EditorInfo.IME_ACTION_UNSPECIFIED;
            if (noAction) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            else {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.performEditorAction(action);
            }
        }
    }

    @Override
    public void onAction(int id) {
        InputConnection ic = getCurrentInputConnection();
        switch (id) {
            case KeyboardView.T_SELECT:
                if (ic != null) ic.performContextMenuAction(android.R.id.selectAll);
                break;
            case KeyboardView.T_COPY:
                if (ic != null) ic.performContextMenuAction(android.R.id.copy);
                break;
            case KeyboardView.T_PASTE:
                if (ic != null) ic.performContextMenuAction(android.R.id.paste);
                break;
            case KeyboardView.T_SETTINGS:
                Intent i = new Intent(this, SettingsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                break;
            default:
                break;
        }
    }
}
