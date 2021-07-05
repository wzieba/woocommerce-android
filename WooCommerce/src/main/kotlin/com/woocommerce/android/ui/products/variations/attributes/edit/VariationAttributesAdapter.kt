package com.woocommerce.android.ui.products.variations.attributes.edit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.woocommerce.android.databinding.AttributeTermSelectionListItemBinding
import com.woocommerce.android.ui.products.variations.attributes.edit.VariationAttributesAdapter.VariationAttributeSelectionViewHolder

class VariationAttributesAdapter(
    var sourceData: MutableList<VariationAttributeSelectionGroup>,
    private val anyAttributeResourceText: String,
    private val onGroupClickListener: (VariationAttributeSelectionGroup) -> Unit
) : RecyclerView.Adapter<VariationAttributeSelectionViewHolder>() {
    override fun getItemCount() = sourceData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VariationAttributeSelectionViewHolder(
            AttributeTermSelectionListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: VariationAttributeSelectionViewHolder, position: Int) {
        sourceData[position].let { holder.bind(it) }
    }

    fun refreshSourceData(sourceData: MutableList<VariationAttributeSelectionGroup>) {
        this.sourceData = sourceData
        notifyDataSetChanged()
    }

    fun refreshSingleAttributeSelectionGroup(modifiedGroup: VariationAttributeSelectionGroup) {
        sourceData.apply {
            indexOf(find { it.attributeName == modifiedGroup.attributeName }).let {
                sourceData[it] = modifiedGroup
                notifyItemChanged(it)
            }
        }
    }

    inner class VariationAttributeSelectionViewHolder(
        val viewBinding: AttributeTermSelectionListItemBinding
    ) : RecyclerView.ViewHolder(viewBinding.root) {
        fun bind(item: VariationAttributeSelectionGroup) {
            viewBinding.attributeOptionsSpinner.apply {
                hint = item.attributeName
                setText(item.selectedOptionDisplayText())
                setClickListener { onGroupClickListener(item) }
            }
        }

        private fun VariationAttributeSelectionGroup.selectedOptionDisplayText() =
            if (isAnyOptionSelected) {
                "$anyAttributeResourceText $attributeName"
            } else {
                selectedOption
            }
    }
}
