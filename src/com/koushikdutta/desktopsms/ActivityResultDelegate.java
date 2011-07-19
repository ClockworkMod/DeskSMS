package com.koushikdutta.desktopsms;

import android.content.Intent;

public interface ActivityResultDelegate {
    public void setOnActivityResultCallback(Callback<Tuple<Integer, Tuple<Integer, Intent>>> callback);
}
