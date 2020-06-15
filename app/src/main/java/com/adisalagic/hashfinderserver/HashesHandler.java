package com.adisalagic.hashfinderserver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HashesHandler {
    //Может быть, для хранения огромного кол-ва хешей ArrayList не подойдет,
    //но он удобен в использовании
    //Использование глабального масива нужно для AidlService
    public static ArrayList<String> hashes = new ArrayList<>();
    public static final String NAME_OF_TEMP_FILE = "tempFile";
    public static boolean verifyHash(String hash){
        Pattern pattern = Pattern.compile("[a-f0-9]{32}");
        Matcher matcher = pattern.matcher(hash);
        return matcher.find();
    }
}
