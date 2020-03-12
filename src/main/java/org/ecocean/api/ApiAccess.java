package org.ecocean.api;

import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.Organization;

/*
import java.util.List;
import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
*/

public class ApiAccess {
    public static final int READ = 0;
    public static final int WRITE = 1;

    public static boolean validAccessValue(int val) {
        return ((val == READ) || (val == WRITE));
    }
}

