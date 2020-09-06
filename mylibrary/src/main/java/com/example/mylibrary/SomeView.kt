package com.example.mylibrary

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView

class SomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        addView(TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_MaterialComponents_Body1)
            setText(R.string.works_fine)
        })
    }
}