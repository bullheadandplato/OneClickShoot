package com.topratedappps.oneclickshot.model;

import java.io.Serializable;

/**
 * Created by bullhead on 11/5/17.
 */

public class Screenshot implements Serializable {
    private String name;
    private String filepath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }
}
