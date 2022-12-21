package com.jieli.otasdk_java.fragments;

import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.jieli.otasdk_java.R;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * @ClassName: FileAdapter
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/22 16:14
 */
public class FileAdapter extends BaseQuickAdapter<File, BaseViewHolder> {
    private int selectedIndex = 0;

    public FileAdapter(@Nullable List<File> data) {
        super(R.layout.item_file_list, data);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, File file) {
        TextView tvName = baseViewHolder.getView(R.id.tv_item_file_name);
        tvName.setText(file.getName());
        int position = baseViewHolder.getAdapterPosition() - getHeaderLayoutCount();
        if (position == selectedIndex) {
            tvName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_check_blue,
                    0
            );
        } else {
            tvName.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    public void setSelectedIndex(int pos) {
        if (selectedIndex != pos) {
            selectedIndex = pos;
            notifyDataSetChanged();
        }
    }

    public File getSelectedItem() {
        File file = null;
        if (getData().size() != 0) {
            file = getItem(selectedIndex);
        }
        return file;
    }
}
