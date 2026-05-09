package com.easysell.paymentadmin.model;

import java.util.ArrayList;
import java.util.List;

public class CursorPage<T> {
    public List<T> items = new ArrayList<>();
    public Long nextCursor;
}
