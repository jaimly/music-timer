package org.ganquan.musictimer.comp

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup


class RadioGroup : LinearLayout {
    /**
     *
     * Returns the identifier of the selected radio button in this group.
     * Upon empty selection, the returned value is -1.
     *
     * @return the unique id of the selected radio button in this group
     * @attr ref android.R.styleable#RadioGroup_checkedButton
     * @see .check
     * @see .clearCheck
     */
    // holds the checked id; the selection is empty by default
    var checkedRadioButtonId: Int = -1
        private set

    // tracks children radio buttons checked state
    private var mChildOnCheckedChangeListener: CompoundButton.OnCheckedChangeListener? = null

    // when true, mOnCheckedChangeListener discards events
    private var mProtectFromCheckedChange = false
    private var mOnCheckedChangeListener: OnCheckedChangeListener? = null
    private var mPassThroughListener: PassThroughHierarchyChangeListener? = null

    /**
     * {@inheritDoc}
     */
    constructor(context: Context?) : super(context) {
        orientation = VERTICAL
        init()
    }

    /**
     * {@inheritDoc}
     */
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        this.checkedRadioButtonId = NO_ID

        orientation = VERTICAL

        init()
    }

    private fun init() {
        mChildOnCheckedChangeListener = CheckedStateTracker()
        mPassThroughListener = PassThroughHierarchyChangeListener()
        super.setOnHierarchyChangeListener(mPassThroughListener)
    }

    /**
     * {@inheritDoc}
     */
    override fun setOnHierarchyChangeListener(listener: OnHierarchyChangeListener?) {
        // the user listener is delegated to our pass-through listener
        mPassThroughListener?.mOnHierarchyChangeListener = listener
    }

    /**
     * {@inheritDoc}
     */
    override fun onFinishInflate() {
        super.onFinishInflate()


        // checks the appropriate radio button as requested in the XML file
        if (this.checkedRadioButtonId != -1) {
            mProtectFromCheckedChange = true
            setCheckedStateForView(this.checkedRadioButtonId, true)
            mProtectFromCheckedChange = false
            setCheckedId(this.checkedRadioButtonId)
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (child is RadioButton) {
            child.setOnTouchListener(object : OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && !child.isChecked()) {
                        child.setChecked(true)
                        checkRadioButton(child)
                        if (mOnCheckedChangeListener != null) {
                            mOnCheckedChangeListener!!.onCheckedChanged(
                                this@RadioGroup,
                                child.getId()
                            )
                        }
                    }
                    return true
                }
            })
        } else if (child is LinearLayout) {
            val childCount = child.getChildCount()
            for (i in 0..<childCount) {
                val view = child.getChildAt(i)
                if (view is RadioButton) {
                    val button = view
                    button.setOnTouchListener(object : OnTouchListener {
                        override fun onTouch(v: View?, event: MotionEvent): Boolean {
                            if (event.getAction() == MotionEvent.ACTION_DOWN && !button.isChecked()) {
                                button.setChecked(true)
                                checkRadioButton(button)
                                if (mOnCheckedChangeListener != null) {
                                    mOnCheckedChangeListener!!.onCheckedChanged(
                                        this@RadioGroup,
                                        button.getId()
                                    )
                                }
                            }
                            return true
                        }
                    })
                }
            }
        }

        super.addView(child, index, params)
    }

    private fun checkRadioButton(radioButton: RadioButton?) {
        var child: View?
        val radioCount = getChildCount()
        for (i in 0..<radioCount) {
            child = getChildAt(i)
            if (child is RadioButton) {
                if (child === radioButton) {
                    // do nothing
                } else {
                    child.setChecked(false)
                }
            } else if (child is LinearLayout) {
                val childCount = child.getChildCount()
                for (j in 0..<childCount) {
                    val view = child.getChildAt(j)
                    if (view is RadioButton) {
                        val button = view
                        if (button === radioButton) {
                            // do nothing
                        } else {
                            button.setChecked(false)
                        }
                    }
                }
            }
        }
    }

    /**
     *
     * Sets the selection to the radio button whose identifier is passed in
     * parameter. Using -1 as the selection identifier clears the selection;
     * such an operation is equivalent to invoking [.clearCheck].
     *
     * @param id the unique id of the radio button to select in this group
     * @see .getCheckedRadioButtonId
     * @see .clearCheck
     */
    fun check(id: Int) {
        // don't even bother
        if (id != -1 && (id == this.checkedRadioButtonId)) {
            return
        }

        if (this.checkedRadioButtonId != -1) {
            setCheckedStateForView(this.checkedRadioButtonId, false)
        }

        if (id != -1) {
            setCheckedStateForView(id, true)
        }

        setCheckedId(id)
    }

    private fun setCheckedId(id: Int) {
        this.checkedRadioButtonId = id
    }

    private fun setCheckedStateForView(viewId: Int, checked: Boolean) {
        val checkedView = findViewById<View?>(viewId)
        if (checkedView != null && checkedView is RadioButton) {
            checkedView.setChecked(checked)
        }
    }

    /**
     *
     * Clears the selection. When the selection is cleared, no radio button
     * in this group is selected and [.getCheckedRadioButtonId] returns
     * null.
     *
     * @see .check
     * @see .getCheckedRadioButtonId
     */
    fun clearCheck() {
        check(-1)
    }

    /**
     *
     * Register a callback to be invoked when the checked radio button
     * changes in this group.
     *
     * @param listener the callback to call on checked state change
     */
    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        mOnCheckedChangeListener = listener
    }

    /**
     * {@inheritDoc}
     */
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(getContext(), attrs)
    }

    /**
     * {@inheritDoc}
     */
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is RadioGroup.LayoutParams
    }

    override fun generateDefaultLayoutParams(): LinearLayout.LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.setClassName(RadioGroup::class.java.getName())
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.setClassName(RadioGroup::class.java.getName())
    }

    /**
     *
     * This set of layout parameters defaults the width and the height of
     * the children to [.WRAP_CONTENT] when they are not specified in the
     * XML file. Otherwise, this class ussed the value read from the XML file.
     *
     *
     *
     * See
     * for a list of all child view attributes that this class supports.
     */
    class LayoutParams : LinearLayout.LayoutParams {
        /**
         * {@inheritDoc}
         */
        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)

        /**
         * {@inheritDoc}
         */
        constructor(w: Int, h: Int) : super(w, h)

        /**
         * {@inheritDoc}
         */
        constructor(w: Int, h: Int, initWeight: Float) : super(w, h, initWeight)

        /**
         * {@inheritDoc}
         */
        constructor(p: ViewGroup.LayoutParams?) : super(p)

        /**
         * {@inheritDoc}
         */
        constructor(source: MarginLayoutParams?) : super(source)

        /**
         *
         * Fixes the child's width to
         * [ViewGroup.LayoutParams.WRAP_CONTENT] and the child's
         * height to  [ViewGroup.LayoutParams.WRAP_CONTENT]
         * when not specified in the XML file.
         *
         * @param a          the styled attributes set
         * @param widthAttr  the width attribute to fetch
         * @param heightAttr the height attribute to fetch
         */
        override fun setBaseAttributes(
            a: TypedArray,
            widthAttr: Int, heightAttr: Int
        ) {
            if (a.hasValue(widthAttr)) {
                width = a.getLayoutDimension(widthAttr, "layout_width")
            } else {
                width = WRAP_CONTENT
            }

            if (a.hasValue(heightAttr)) {
                height = a.getLayoutDimension(heightAttr, "layout_height")
            } else {
                height = WRAP_CONTENT
            }
        }
    }

    /**
     *
     * Interface definition for a callback to be invoked when the checked
     * radio button changed in this group.
     */
    interface OnCheckedChangeListener {
        /**
         *
         * Called when the checked radio button has changed. When the
         * selection is cleared, checkedId is -1.
         *
         * @param group     the group in which the checked radio button has changed
         * @param checkedId the unique identifier of the newly checked radio button
         */
        fun onCheckedChanged(group: org.ganquan.musictimer.comp.RadioGroup?, checkedId: Int)
    }

    private inner class CheckedStateTracker : CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
            // prevents from infinite recursion
            if (mProtectFromCheckedChange) {
                return
            }

            mProtectFromCheckedChange = true
            if (checkedRadioButtonId != -1) {
                setCheckedStateForView(checkedRadioButtonId, false)
            }
            mProtectFromCheckedChange = false

            val id = buttonView.id
            setCheckedId(id)
        }
    }

    /**
     *
     * A pass-through listener acts upon the events and dispatches them
     * to another listener. This allows the table layout to set its own internal
     * hierarchy change listener without preventing the user to setup his.
     */
    private inner class PassThroughHierarchyChangeListener : OnHierarchyChangeListener {
        var mOnHierarchyChangeListener: OnHierarchyChangeListener? = null

        /**
         * {@inheritDoc}
         */
        override fun onChildViewAdded(parent: View?, child: View?) {
            if (parent === this@RadioGroup && child is RadioButton) {
                var id = child.getId()
                // generates an id if it's missing
                if (id == NO_ID) {
                    id = child.hashCode()
                    child.setId(id)
                }
                child.setOnCheckedChangeListener(
                    mChildOnCheckedChangeListener
                )
            }

            if (mOnHierarchyChangeListener != null) {
                mOnHierarchyChangeListener!!.onChildViewAdded(parent, child)
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun onChildViewRemoved(parent: View?, child: View?) {
            if (parent === this@RadioGroup && child is RadioButton) {
                child.setOnCheckedChangeListener(null)
            }

            if (mOnHierarchyChangeListener != null) {
                mOnHierarchyChangeListener!!.onChildViewRemoved(parent, child)
            }
        }
    }
}