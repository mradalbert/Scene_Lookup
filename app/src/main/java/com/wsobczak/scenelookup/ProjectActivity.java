package com.wsobczak.scenelookup;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

public class ProjectActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private String[] PERMISSIONS_LIST = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    private Spinner spinnerProject;

    private ArrayList<Cell> allFilesPaths;
    private ArrayList<String> projectsList = new ArrayList<>();

    private File mGalleryFolder;
    private String currentProjectPath;
    private ArrayAdapter<String> projectAdapter;
    private String savedProjectSpinnerState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project);
        spinnerProject = findViewById(R.id.spinnerProject);
        spinnerProject.setOnItemSelectedListener(this);
        requestPermissions(PERMISSIONS_LIST);
        createImageGallery();
        currentProjectPath = mGalleryFolder.getAbsolutePath();

        if (savedInstanceState != null) {
            // Restore value of members from saved state
            savedProjectSpinnerState = savedInstanceState.getString("projectSpinnerState");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        createImageGallery();
        populateSpinnerProject();

        if (savedProjectSpinnerState != null) {
            spinnerProject.setSelection(projectAdapter.getPosition(savedProjectSpinnerState));
            savedProjectSpinnerState = null;
        }

        showImages();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults[0] + grantResults[1] + grantResults[2] != 0) {
                finish();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        String previouslySelected = new String();
        if (spinnerProject.getSelectedItem() != null) previouslySelected = spinnerProject.getSelectedItem().toString();

        savedInstanceState.putString("projectSpinnerState", previouslySelected);
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public void openCameraViewfinder(View view) {
        Intent myIntent = new Intent(this, MainActivity.class);
        myIntent.putExtra("workingDirectory", currentProjectPath); //Optional parameters
        startActivity(myIntent);
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void requestPermissions(String[] PERMISSIONS) {
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
    }

    private void showImages() {
        allFilesPaths = new ArrayList<>();
        allFilesPaths = listAllFiles(currentProjectPath);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.gallery);
        recyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getApplicationContext(), 3);
        recyclerView.setLayoutManager(layoutManager);

        recyclerView.setItemViewCacheSize(100);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        ArrayList<Cell> cells = prepareData();
        MyAdapter adapter = new MyAdapter(getApplicationContext(), cells);
        recyclerView.setAdapter(adapter);
    }

    private ArrayList<Cell> prepareData() {

        ArrayList<Cell> allImages = new ArrayList<>();
        for (Cell c: allFilesPaths) {
            Cell cell = new Cell();
            cell.setTitle(c.getTitle());
            cell.setPath(c.getPath());
            allImages.add(cell);
        }
        return allImages;
    }

    private ArrayList<Cell> listAllFiles(String pathName) {
        String extension;
        ArrayList<Cell> allFiles = new ArrayList<>();
        File file = new File(pathName);
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isDirectory()) {
                    extension = f.getAbsolutePath().substring(f.getAbsolutePath().lastIndexOf(".") + 1);
                    byte[] bytes = extension.getBytes();
                    if (extension.equals("jpg")) {
                        Cell cell = new Cell();
                        cell.setTitle(f.getName());
                        cell.setPath(f.getAbsolutePath());
                        allFiles.add(cell);
                    }
                }
            }
        }
        return allFiles;
    }

    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mGalleryFolder = new File(storageDirectory, getResources().getString(R.string.app_name));
        if (!mGalleryFolder.exists()) {
            mGalleryFolder.mkdirs();
        }
    }

    private void refreshProjectList() {
        projectsList.clear();
        projectsList.add("~Default~");
        File[] files = mGalleryFolder.listFiles();
        for (File inFile : files) {
            if (inFile.isDirectory()) {
                projectsList.add(inFile.getName());
            }
        }
    }

    private void populateSpinnerProject() {
        String previouslySelected = new String();
        if (spinnerProject.getSelectedItem() != null) previouslySelected = spinnerProject.getSelectedItem().toString();

        refreshProjectList();
        projectAdapter= new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, projectsList);
        projectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProject.setAdapter(projectAdapter);

        if (!previouslySelected.isEmpty()) spinnerProject.setSelection(projectAdapter.getPosition(previouslySelected));
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String result = parent.getItemAtPosition(position).toString();

        if (result == "~Default~") {
            currentProjectPath =  mGalleryFolder.getAbsolutePath();
        } else {
            currentProjectPath = mGalleryFolder.getAbsolutePath() + "/" + result;
        }

        showImages();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public void deleteProject(final View view) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:

                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which){
                                    case DialogInterface.BUTTON_POSITIVE:

                                        if(!currentProjectPath.equals(mGalleryFolder.getAbsolutePath())) {
                                            deleteRecursively(currentProjectPath);
                                            populateSpinnerProject();
                                        } else {
                                            Toast.makeText(view.getContext(), "You can not delete projects root directory!", Toast.LENGTH_SHORT).show();
                                        }
                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        return;
                                }
                            }
                        };

                        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                        builder.setMessage("Are you REALLY sure?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();

                    case DialogInterface.BUTTON_NEGATIVE:
                        return;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
        builder.setMessage("Are you sure? All project data will be lost.").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
    }

    public void addProject(final View view) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New project name");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String tempText = input.getText().toString();
                File tempFile = new File(mGalleryFolder.getAbsolutePath() + "/" + tempText);

                if (tempText.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Project name can not be empty", Toast.LENGTH_SHORT).show();
                    addProject(view);
                    return;
                }

                if (tempFile.exists()) {
                    Toast.makeText(getApplicationContext(), "Project already exists", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    tempFile.mkdirs();
                    populateSpinnerProject();
                    spinnerProject.setSelection(projectAdapter.getPosition(tempText));
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void deleteRecursively(String path) {
        File currentFile = new File(path);
        if (currentFile.isDirectory()) {
            File files[] = currentFile.listFiles();
            for (File f : files) {
                deleteRecursively(f.getAbsolutePath());
            }
        }
        currentFile.delete();
    }
}
