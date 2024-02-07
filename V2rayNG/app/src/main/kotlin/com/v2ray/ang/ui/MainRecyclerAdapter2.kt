package com.v2ray.ang.ui

import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tencent.mmkv.MMKV
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerMain2Binding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.viewmodel.SubConfig

class MainRecyclerAdapter2(val activity: MainActivity, val itemList: ArrayList<SubConfig>) :
    RecyclerView.Adapter<MainRecyclerAdapter2.BaseViewHolder>(),
    ItemTouchHelperAdapter {

    private var mActivity: MainActivity = activity

    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    var isRunning = false
    lateinit var holderMain: MainViewHolder
    var pos: Int = 0
    override fun getItemCount() = itemList.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val guid = mActivity.mainViewModel.serversCache[position].guid
            val config = mActivity.mainViewModel.serversCache[position].config
            pos = position
            holderMain = holder


            Log.d("TAG", "absoluteAdapterPosition  ${holder.absoluteAdapterPosition.toString()}")
            Log.d("TAG", "bindingAdapterPosition ${holder.bindingAdapterPosition.toString()}")
            Log.d("TAG", "holder.position  ${holder.position.toString()}")
            Log.d("TAG", "position  ${position.toString()}")
            Log.d("TAG", "position layoutPosition ${holder.layoutPosition}")
            Log.d("TAG", itemList[holder.bindingAdapterPosition].getId()!!.toInt().toString())

            if (itemList[position].getId()!!.toInt() == 0) {
                holder.itemMainBinding.infoContainer.setPadding(32, 0, 0, 0);
            } else {
                holder.itemMainBinding.infoContainer.setPadding(0, 0, 0, 0);
            }
            if (itemList[position].getId()!!.toInt() == itemList.size - 1) {
                holder.itemMainBinding.infoContainer.setPadding(0, 0, 32, 0);
            } else {
                if (itemList[position].getId()!!.toInt() != 0) {
                    holder.itemMainBinding.infoContainer.setPadding(0, 0, 0, 0);
                }
            }

            holder.itemMainBinding.tvName.text = config.remarks
            holder.itemMainBinding.tvName.text = itemList[position].getCountry()
            holder.itemMainBinding.tvCity.text = itemList[position].getCity()
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            Glide.with(mActivity).load(mActivity.itemList[position].getImg())
                .into(holder.itemMainBinding.imgCountry);

            if (guid == mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.item_selected_not_connect)
            } else {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.item_unselected)
            }

            holder.itemMainBinding.infoContainer.setOnClickListener {
                if (isRunning) {
                    mActivity.toast("Please First Disconnect For Change Server")
                } else {
                    val selected = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
                    if (guid != selected) {
                        mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
                        if (!TextUtils.isEmpty(selected)) {
                            notifyItemChanged(mActivity.mainViewModel.getPosition(selected!!))
                        }
                        notifyItemChanged(mActivity.mainViewModel.getPosition(guid))
                    }
                }
            }
        }
    }

    fun changeTheme(isRunning: Boolean, holder: MainViewHolder, position: Int) {
        Log.d(
            "TAGGGG",
            "changeTheme GUID IS:" + mActivity.mainViewModel.serversCache[position].guid
        )
        Log.d(
            "TAGGGG",
            "changeTheme KEY_SELECTED_SERVER IS:" + mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
        )
        if (mActivity.mainViewModel.serversCache[position].guid == mainStorage?.decodeString(
                MmkvManager.KEY_SELECTED_SERVER
            )
        ) {
            if (isRunning) {
                holder.itemMainBinding.imageViewConnect.visibility = View.VISIBLE
                holder.itemMainBinding.tvConnect.visibility = View.VISIBLE
                holder.itemMainBinding.tvCity.visibility = View.INVISIBLE
            } else {
                holder.itemMainBinding.imageViewConnect.visibility = View.INVISIBLE
                holder.itemMainBinding.tvConnect.visibility = View.INVISIBLE
                holder.itemMainBinding.tvCity.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return MainViewHolder(
            ItemRecyclerMain2Binding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }


    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
        }

        fun onItemClear() {
        }

    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMain2Binding) :
        BaseViewHolder(itemMainBinding.root),
        ItemTouchHelperViewHolder {
        fun onItemClick() {
        }
    }

    override fun onItemDismiss(position: Int) {
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        return true
    }

    override fun onItemMoveCompleted() {
    }

    override fun getItemViewType(position: Int): Int {
        return super.getItemViewType(position)
    }

    override fun getItemId(position: Int): Long {
        return super.getItemId(position)
    }

}