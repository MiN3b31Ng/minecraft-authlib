package com.mojang.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: arasulov
 * Date: 01.09.15
 * Time: 1:25
 * To change this template use File | Settings | File Templates.
 */

public class HostsHandler {
/**
 * @return returns Properties object to read
 * */
    public static Properties getProperties(String fileName) throws IOException {
        InputStream is = new FileInputStream(fileName);
        Properties props = new Properties();
        props.load(is);
        return props;
    }

}
