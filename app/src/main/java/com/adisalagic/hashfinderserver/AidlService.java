package com.adisalagic.hashfinderserver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.adisalagic.IFindHash;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class AidlService extends Service {

    File file;
    boolean isReading = false;
    public void readFile() {
        isReading = true;
        HashesHandler.hashes.clear();
        file   = new File(getCacheDir() + "/" + HashesHandler.NAME_OF_TEMP_FILE + ".txt");
        try {
            FileReader reader  = new FileReader(file);
            Scanner    scanner = new Scanner(reader);
            while (scanner.hasNextLine()) {
                String hash = scanner.nextLine();
                if (HashesHandler.verifyHash(hash)) {
                    HashesHandler.hashes.add(hash);
                    Log.i("Hash", hash);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        isReading = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IFindHash.Stub() {
            @Override
            public boolean findHash(String hash) {
                readFile();
                boolean result = false;
                for (String hashed : HashesHandler.hashes) {
                    if (hash.equals(hashed)) {
                        result = true;
                        break;
                    }
                }
                if (!result) {
                    HashesHandler.hashes.add(hash);
                    addToFile(file, hash); //Все бы было проще, если бы у нас был один процесс
                }
                return result;
            }
        };
    }

    private void rewriteFileAgain(File file) {
        try {
            FileWriter fileWriter = new FileWriter(file);
            while (!file.canWrite()){
                //Нужно подождать, если файл уже пишется, иначе начнуться проблемы
            }
            for (String line : HashesHandler.hashes) {
                fileWriter.write(line + "\n");
            }
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addToFile(File file, String line){
        try {
            FileWriter fileWriter = new FileWriter(file, true);
            fileWriter.write(line + "\n");
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }
}
