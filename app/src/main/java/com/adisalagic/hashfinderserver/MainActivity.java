package com.adisalagic.hashfinderserver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.codekidlabs.storagechooser.StorageChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {
    File temp;
    Thread thread = null;
    boolean isFileBeenRead = true;
    public void createTempFile() throws IOException {
        File dir = getCacheDir();
        temp = new File(dir.getAbsolutePath() + "/" + HashesHandler.NAME_OF_TEMP_FILE + ".txt");
        temp.deleteOnExit();
        PrintWriter writer = new PrintWriter(temp);
        writer.print("");
        writer.close();
    }

    public void addToTempFile() {
        try {
            while (!isFileBeenRead){
                Log.i("TDD", "Waiting");
                //Я не знаю, как ждать по-другому
            }
            FileWriter writer = new FileWriter(temp);
            writer.write("");
            for (String hash : HashesHandler.hashes) {
                writer.write(hash + "\n");
                Log.i("TDD", hash);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            createTempFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final int TXT = 9212;

    public void onImportClick(View view) {
        //Использование стандартного проводника, чтобы добыть txt файл
        if (checkPermissions()) {
            Intent intent = new Intent().setAction(Intent.ACTION_GET_CONTENT).setType("text/plain");
            startActivityForResult(Intent.createChooser(intent, "Select TXT file"), TXT);
        } else {
            if (android.os.Build.VERSION.SDK_INT > 23)
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 12);
        }
    }

    public void onExportClick(View view) {
        if (checkPermissions()) {
            //Использование библиотеки, чтобы выбрать папку
            StorageChooser chooser = new StorageChooser.Builder()
                    .withFragmentManager(getFragmentManager())
                    .withActivity(this)
                    .withMemoryBar(true)
                    .allowCustomPath(true)
                    .skipOverview(true)
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build();
            final Activity activity = this;
            chooser.setOnSelectListener(new StorageChooser.OnSelectListener() {
                @Override
                public void onSelect(String path) {
                    final Button ex = findViewById(R.id.export);
                    final Button im = findViewById(R.id.importHash);
                    //Ищу кнопки каждый раз я не всегда, обычно выношу кнопки за методы в класс
                    //И ищу кнопки в onCreate(); методе
                    ex.setEnabled(false);
                    im.setEnabled(false);
                    File file = new File(path);
                    writeToFile(file, ex, im, activity);
                }
            });
            chooser.show();
        } else {
            if (android.os.Build.VERSION.SDK_INT > 23)
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 12);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }
        final Button export = findViewById(R.id.export);
        export.setEnabled(false);
        final Button importB = findViewById(R.id.importHash);
        importB.setEnabled(false);
        if (requestCode == TXT) {
            Uri  uri  = data.getData();
            File file = null;
            try {
                //FileUtil использует отличные методы, чтобы создать из uri файл
                file = FileUtil.from(this, uri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
//                if (file != null && file.canRead()) {
//                    thread.interrupt();
//                }
                if (temp.delete()){
                    createTempFile();
                }

                FileReader reader = new FileReader(file);
                FileOutputStream fileOutputStream = new FileOutputStream(temp);
                FileChannel    channel = fileOutputStream.getChannel();
                final FileLock          lock    = channel.lock();
                final ArrayList<String> strings = new ArrayList<>();
                final Scanner           scanner = new Scanner(reader);
                final TextView textView = findViewById(R.id.log);
                final Activity activity = this;
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {

                        int added   = 0;
                        int skipped = 0;
                        while (scanner.hasNextLine()) {
                            final String line = scanner.nextLine();
                            if (HashesHandler.verifyHash(line)) {
                                added++;
                                final int finalAdded   = added;
                                final int finalSkipped = skipped;
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        textView.setText(getString(R.string.loading_in_proccess, line, finalAdded, finalSkipped));
                                    }
                                });
                                strings.add(line);
                            } else {
                                //Скипаем, если не подходит под RegEx
                                skipped++;
                            }

                        }
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(R.string.loading_last_task);
                            }
                        });
                        final int finalAdded1   = added;
                        final int finalSkipped1 = skipped;

                        textView.post(new Runnable() {
                            @Override
                            public void run() {
                                HashesHandler.hashes = strings;

                                //Потому что читатель в отдельном потоке не дает писателю
                                //нормально писать файл, из-за чего мы теряем хеши
                                addToTempFile(); //Нельзя добавлять в файл в AsyncTask, потому что пишет не полностью
                                textView.setText(getString(R.string.loading_ended, finalAdded1, finalSkipped1));
                                export.setEnabled(true);
                                importB.setEnabled(true);
                                try {
                                    lock.release();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }

    public boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT > 23) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }

    }

    @Override
    protected void onPostResume() {
        //Постоянная проверка на размер массива. Нужна для дебага
        super.onPostResume();

        //Бесполезно, ибо массив находиться в другом процессе
        final TextView textView = findViewById(R.id.log);
        textView.setText(R.string.loading_file);
        final Activity activity = this;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {

                readFile();

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(getString(R.string.size_check, HashesHandler.hashes.size()));
                        //По каким-то странным причинам отображает не верно, если я работаю с миллионом
                        //хешей. А на дебаге 1м хешей тестировать не особо хочется
                        thread = null;
                    }
                });
            }
        });
        thread.start();

    }

    public void readFile() {
        isFileBeenRead = false;
        ArrayList<String> strings = new ArrayList<>();
        try {
            Scanner scanner = new Scanner(temp);
            while (scanner.hasNextLine()) {
                String hash = scanner.nextLine();
                if (HashesHandler.verifyHash(hash)) {
                    strings.add(hash);
                    Log.i("Hsh", hash);
                }
            }
            scanner.reset();
            scanner.close();
            isFileBeenRead = true;
            HashesHandler.hashes = strings; //Потому что читатель в отдельном потоке не дает писателю
            // нормально писать файл, из-за чего мы теряем хеши
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToFile(File file, final Button ex, final Button im, final Activity activity) {
        if (!file.canWrite()){
            thread.interrupt();
        }
        if (file.isDirectory()) {
            final File newFileHash = new File(file.getAbsolutePath() + "/" +
                    Calendar.getInstance().get(Calendar.SECOND) + Calendar.getInstance().get(Calendar.HOUR) + Calendar.getInstance().get(Calendar.DATE) + ".txt");
            try {
                if (newFileHash.createNewFile()) {
                    final TextView textView = findViewById(R.id.log);
                    textView.setText(getString(R.string.new_file, newFileHash.getAbsolutePath()));
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {

                                FileWriter              writer = new FileWriter(newFileHash);
                                final ArrayList<String> hashes = HashesHandler.hashes;
                                for (int i = 0; i < hashes.size(); i++) {
                                    final String line   = hashes.get(i);
                                    final int    remain = hashes.size() - (i + 1);
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            //Мой девайс еле выдерживает это. Такую надпись ему очень сложно наносить.
                                            //Скорее всего, чтобы он так не умирал, нужно несколько TextView
                                            //С другой стороны, я тестирую на 999999 хешей, поэтому он так и умирает
                                            textView.setText(getString(R.string.new_file_process, newFileHash.getAbsolutePath(),
                                                    line, remain));
                                        }
                                    });
                                    writer.write(line + "\n");
                                }
                                writer.close();
                            } catch (Exception ignored) {

                            }
                            textView.post(new Runnable() {
                                @Override
                                public void run() {
                                    textView.setText(getString(R.string.new_file_ended, newFileHash.getAbsolutePath()));
                                    ex.setEnabled(true);
                                    im.setEnabled(true);
                                }
                            });
                        }
                    });

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}