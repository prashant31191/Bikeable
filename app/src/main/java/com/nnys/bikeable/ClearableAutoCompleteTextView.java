package com.nnys.bikeable;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;

import com.google.android.gms.location.places.AutocompletePrediction;

import java.util.ArrayList;

/**
 * This class manages the autocomplete text view, and enables clearing the text view in one click
 *
 * CREDIT:
 * sub class of {@link android.widget.AutoCompleteTextView} that includes a clear (dismiss / close) button with
 * a OnClearListener to handle the event of clicking the button
 * based on code from https://gist.github.com/mderazon/6700044
 * @author Michael Derazon
 *
 */
public class ClearableAutoCompleteTextView extends AutoCompleteTextView {
    // was the text just cleared?
    boolean doClear = false;
    boolean isTouchedAgain = false;
    AutocompletePrediction prediction;
    ClearableAutoCompleteTextView currView = this;
    Context context;
    SearchHistoryCollector searchHistoryCollector = null;

    // if not set otherwise, the default clear listener clears the text in the
    // text view
    private OnClearListener defaultClearListener = new OnClearListener() {

        @Override
        public void onClear() {
            ClearableAutoCompleteTextView view = ClearableAutoCompleteTextView.this;
            view.clearListSelection();
            view.dismissDropDown();
            view.hideClearButton();
            prediction = null;
        }
    };

    public OnClearListener onClearListener = defaultClearListener;

    public OnClearExtraListener onClearExtraListener;

    /* The image we defined for the clear button */
    public Drawable imgClearButton = ContextCompat.getDrawable(getContext(),
            R.drawable.abc_ic_clear_mtrl_alpha);


    public interface OnClearListener {
        void onClear();
    }

    public interface OnClearExtraListener {
        void onClearExtra();
    }

    /* Required methods, not used in this implementation */
    public ClearableAutoCompleteTextView(Context context) {
        super(context);
        this.context = context;
        init();
    }

    /* Required methods, not used in this implementation */
    public ClearableAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /* Required methods, not used in this implementation */
    public ClearableAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void init() {
        // Set the bounds of the button
//        this.setCompoundDrawablesWithIntrinsicBounds(null, null,
//                imgClearButton, null);
        this.setText("");
        this.hideClearButton();

        this.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0){
                    currView.onClearListener.onClear();
                    doClear = false;
                }
                else {
                    currView.showClearButton();
                    if(s.length() < getResources().getInteger(R.integer.auto_complete_thresh)){
                        showOnlyFixedResults();
                    }
                }
            }
        });

        this.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                prediction = (AutocompletePrediction) parent.getItemAtPosition(position);
            }
        });


        // if the clear button is pressed, fire up the handler. Otherwise do nothing
        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ClearableAutoCompleteTextView et = ClearableAutoCompleteTextView.this;

                if (event.getActionMasked() == MotionEvent.ACTION_DOWN){

                    // dismiss if touched for the second time
                    if (isTouchedAgain){
                        et.dismissDropDown();
                        isTouchedAgain = isTouchedAgain ^ true;
                        return false;
                    }

                    isTouchedAgain = isTouchedAgain ^ true;

                    if (et.getCompoundDrawables()[2] == null) {
                        showOnlyFixedResults();
                        return false;
                    }
                }

                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }

                if (event.getX() > et.getWidth() - et.getPaddingRight() - imgClearButton.getIntrinsicWidth()) {
                    currView.onClearExtraListener.onClearExtra();
                    doClear = true;
                    currView.setText("");
                }
                return false;
            }
        });
    }

    private void showOnlyFixedResults() {
        PlaceAutocompleteAdapter currAdapter = (PlaceAutocompleteAdapter) currView.getAdapter();
        ArrayList<AutocompletePrediction> fixedResults = new ArrayList();
        fixedResults.addAll(currAdapter.getFixedResults());
        if (searchHistoryCollector != null && !searchHistoryCollector.getOnlineHistory().isEmpty()){
            fixedResults.addAll(searchHistoryCollector.getOnlineHistory());
        }

        currAdapter.setResultsList(fixedResults);
        currView.getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                currView.showDropDown();
            }
        }, 500);
    }

    public void setImgClearButton(Drawable imgClearButton) {
        this.imgClearButton = imgClearButton;
    }

    public void setImgClearButtonColor(int color){
        this.imgClearButton.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    }

    public void setOnClearListener(final OnClearListener clearListener) {
        this.onClearListener = clearListener;
    }

    public void setOnClearExtraListener(final OnClearExtraListener extraListener){
        this.onClearExtraListener = extraListener;
    }

    public void hideClearButton() {
        this.setCompoundDrawables(null, null, null, null);
    }

    public void showClearButton() {
        this.setCompoundDrawablesWithIntrinsicBounds(null, null, imgClearButton, null);
    }

    public AutocompletePrediction getPrediction() {
        return prediction;
    }

    public void setPrediction(AutocompletePrediction prediction, boolean setText) {
        this.prediction = prediction;
        if (setText)
            this.setText(prediction.getDescription(), false);
    }

    public void setSearchHistoryCollector(SearchHistoryCollector searchHistoryCollector) {
        this.searchHistoryCollector = searchHistoryCollector;
    }
}