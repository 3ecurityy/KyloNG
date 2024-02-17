package com.v2ray.ang.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tencent.mmkv.MMKV
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerConnectBinding
import com.v2ray.ang.databinding.ItemRecyclerMain2Binding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.viewmodel.SubConfig

class MainRecyclerAdapter2(val activity: MainActivity, private val itemList: ArrayList<SubConfig>) :
    RecyclerView.Adapter<MainRecyclerAdapter2.BaseViewHolder>(),
    ItemTouchHelperAdapter {

    private var mActivity: MainActivity = activity

    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    companion object {
        private const val VIEW_TYPE_CONNECT = 1
        private const val VIEW_TYPE_DISCONNECT = 2
    }

    var isRunning = false
    lateinit var holderMain: MainViewHolder
    lateinit var holderConnect: OnConnectViewHolder
    var pos: Int = 0
    override fun getItemCount() = itemList.size

    var guid2: String = ""

    var selectedPosition = -1
    var lastSelectedPosition = -1
    var appStart: Boolean = true

    private lateinit var shPref: SharedPreferences
    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val guid = mActivity.mainViewModel.serversCache[position].guid
            val config = mActivity.mainViewModel.serversCache[position].config
            mActivity.mainViewModel.serversCache[position].config.remarks =
                itemList[position].getCountry() + itemList[position].getCity()

            pos = position
            holderMain = holder


            shPref = mActivity.getSharedPreferences("MyPref", Context.MODE_PRIVATE);
            val sEdit = shPref.edit()


            setPadding(position)
            initData(position)

            if (guid == mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.item_selected)
            } else {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.item_unselected)
            }

            if (appStart) {
                appStart = false
                for (i in 0 until itemList.size) {
                    if (itemList[i].getId().equals(mActivity.selectedItemUUId)) {
                        selectedPosition = i
                        mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
                        break
                    }
                }
            }


            if (position == selectedPosition) {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.item_selected)
                mainStorage.encode(
                    "UUID2",
                    itemList[selectedPosition].getCountry() + " | " + itemList[selectedPosition].getCity()
                )
            } else {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.item_unselected)
            }

            holder.itemMainBinding.infoContainer.setOnClickListener {
                lastSelectedPosition = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (isRunning) {
                    mActivity.toast("Please First Disconnect For Change Server")
                } else {
                    val selected = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
                    if (guid != selected) {
                        mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
                        // mainStorage?.encode("UUID", itemList[position].getId())
                        sEdit.putString("UUID", itemList[position].getId())
                        mainStorage.encode(
                            "UUID2",
                            itemList[position].getCountry() + " | " + itemList[position].getCity()
                        )
                        sEdit.apply()
                        if (!TextUtils.isEmpty(selected)) {
                            notifyItemChanged(mActivity.mainViewModel.getPosition(selected!!))
                        }
                        notifyItemChanged(mActivity.mainViewModel.getPosition(guid))
                        for (i in 0 until itemCount) {
                            itemList[i].setIsActive(false)
                        }
                        itemList[position].setIsActive(true)
                    }
                }
            }


        }

    }

    private fun initData(position: Int) {
        holderMain.itemMainBinding.tvName.text = itemList[position].getCountry()
        holderMain.itemMainBinding.tvCity.text = itemList[position].getCity()
        Glide.with(mActivity).load(mActivity.itemList[position].getImg())
            .into(holderMain.itemMainBinding.imgCountry);
    }

    private fun setPadding(position: Int) {
        if (itemList[position].getSortOrder()!!.toInt() == 0) {
            holderMain.itemMainBinding.infoContainer.setPadding(32, 0, 0, 0);
        } else {
            holderMain.itemMainBinding.infoContainer.setPadding(0, 0, 0, 0);
        }
        if (itemList[position].getSortOrder()!!.toInt() == itemList.size - 1) {
            holderMain.itemMainBinding.infoContainer.setPadding(0, 0, 32, 0);
        } else {
            if (itemList[position].getSortOrder()!!.toInt() != 0) {
                holderMain.itemMainBinding.infoContainer.setPadding(0, 0, 0, 0);
            }
        }
    }

    fun changeTheme() {
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_CONNECT ->
                MainViewHolder(
                    ItemRecyclerMain2Binding.inflate(
                        LayoutInflater.from(
                            parent.context
                        ), parent, false
                    )
                )

            else ->
                OnConnectViewHolder(
                    ItemRecyclerConnectBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
        }

        fun onItemClear() {
        }

    }


    class MainViewHolder(val itemMainBinding: ItemRecyclerMain2Binding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class OnConnectViewHolder(private val itemConnectBinding: ItemRecyclerConnectBinding) :
        BaseViewHolder(itemConnectBinding.root)


    override fun onItemDismiss(position: Int) {
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        return true
    }

    override fun onItemMoveCompleted() {
    }

    override fun getItemViewType(position: Int): Int {
        /* return if (mActivity.mainViewModel.isRunning.value != true) {
             VIEW_TYPE_CONNECT
         } else {
             VIEW_TYPE_DISCONNECT
         }*/
        return VIEW_TYPE_CONNECT
    }
}