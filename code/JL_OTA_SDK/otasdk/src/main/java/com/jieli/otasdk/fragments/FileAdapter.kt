package com.jieli.otasdk.fragments

import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.jieli.otasdk.R
import java.io.File

/**
 * 文件适配器
 *
 * @author zqjasonZhong
 * @since 2021/1/7
 */
class FileAdapter(data: MutableList<File>?) :
    BaseQuickAdapter<File, BaseViewHolder>(R.layout.item_file_list, data) {

    private var selectedIndex = 0


    override fun convert(holder: BaseViewHolder, item: File) {
        item.run {
            val tvName = holder.getView<TextView>(R.id.tv_item_file_name)
            tvName.text = name
            val position = holder.adapterPosition - headerLayoutCount
            if (position == selectedIndex) {
                tvName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_check_blue,
                    0
                )
            } else {
                tvName.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }

    fun setSelectedIndex(pos: Int) {
        if (selectedIndex != pos) {
            selectedIndex = pos
            notifyDataSetChanged()
        }
    }

    fun getSelectedItem(): File? {
        return if (data.size == 0) null else getItem(selectedIndex)
    }
}