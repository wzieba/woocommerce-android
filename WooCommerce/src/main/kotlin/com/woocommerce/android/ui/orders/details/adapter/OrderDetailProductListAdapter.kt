package com.woocommerce.android.ui.orders.details.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.woocommerce.android.R
import com.woocommerce.android.model.Order
import com.woocommerce.android.tools.ProductImageMap
import com.woocommerce.android.ui.orders.OrderDetailProductItemView
import com.woocommerce.android.ui.orders.OrderProductActionListener
import org.wordpress.android.util.PhotonUtils
import java.math.BigDecimal

class OrderDetailProductListAdapter(
    private val orderItems: List<Order.Item>,
    private val productImageMap: ProductImageMap,
    private val formatCurrencyForDisplay: (BigDecimal) -> String,
    private val productItemListener: OrderProductActionListener
) : RecyclerView.Adapter<OrderDetailProductListAdapter.ViewHolder>() {
    class ViewHolder(val view: OrderDetailProductItemView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: OrderDetailProductItemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.order_detail_product_list_item, parent, false)
            as OrderDetailProductItemView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = orderItems[position]
        val imageSize = holder.view.resources.getDimensionPixelSize(R.dimen.image_minor_100)
        val productImage = PhotonUtils.getPhotonImageUrl(productImageMap.get(item.uniqueId), imageSize, imageSize)
        holder.view.initView(orderItems[position], productImage, formatCurrencyForDisplay)
        holder.view.setOnClickListener {
            if (item.isVariation) {
                productItemListener.openOrderProductVariationDetail(item.productId, item.variationId)
            } else {
                productItemListener.openOrderProductDetail(item.productId)
            }
        }
    }

    override fun getItemCount() = orderItems.size

    fun notifyProductChanged(productId: Long) {
        for (position in orderItems.indices) {
            if (orderItems[position].productId == productId) {
                notifyItemChanged(position)
            }
        }
    }
}
