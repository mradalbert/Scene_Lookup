package com.wsobczak.scenelookup;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {

    private ArrayList<Cell> galleryList;
    private Context context;
    private OnPhotoDeletedListener mPhotoDeletedListener;

    public MyAdapter(Context context, ArrayList<Cell> galleryList) {
        this.galleryList = galleryList;
        this.context = context;
    }

    @NonNull
    @Override
    public MyAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) { //was parent / viewType
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.cell, viewGroup, false);
        return new MyAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, final int i) {//was holder/position
        viewHolder.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        setImageFromPath(galleryList.get(i).getPath(), viewHolder.img);
        viewHolder.img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String[] options = {"Delete file", "View File", "Share"};

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Pick a color");
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                deleteFile(galleryList.get(i).getPath());
                                break;
                            case 1:
                                //openImageInExternalApp(galleryList.get(i).getPath());
                                break;
                            case 3:
                                //TODO
                                break;
                        }
                    }
                });
                builder.show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return galleryList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView img;

        public ViewHolder(View view) {
            super(view);

            img = (ImageView) view.findViewById(R.id.img);
        }
    }

    private void setImageFromPath(String path, ImageView image) {
        File imgFile = new File(path);
        if (imgFile.exists()) {
            Bitmap myBitmap = ImageHelper.decodeSampledBitmapFromPath(imgFile.getAbsolutePath(), 200, 200);
            image.setImageBitmap(myBitmap);
        }
    }

    private void deleteFile(final String filePath) {
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

                                        File file = new File(filePath);
                                        file.delete();
                                        mPhotoDeletedListener.onPhotoDeleted();
                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        return;
                                }
                            }
                        };

                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setMessage("Are you REALLY sure?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();

                    case DialogInterface.BUTTON_NEGATIVE:
                        return;
                }
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage("Are you sure? All project data will be lost.").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();

    }

    private void openImageInExternalApp(String filePath){
        File file = new File(filePath);
        Uri uri = Uri.fromFile(file);
        Intent intent = new Intent(Intent.ACTION_VIEW);

        intent.setDataAndType(uri, "image/jpeg");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public interface OnPhotoDeletedListener {
        void onPhotoDeleted();
    }

    public void setOnPhotoDeletedListener(OnPhotoDeletedListener l) {
        mPhotoDeletedListener = l;
    }
}
