package com.wsobczak.scenelookup;

import java.util.Comparator;

public class Cell {

    private String title, path;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public static class CellComparator implements Comparator<Cell>
    {
        public int compare(Cell left, Cell right) {
            return Long.signum(left.getTitle().compareTo(right.getTitle()));
        }
    }
}
