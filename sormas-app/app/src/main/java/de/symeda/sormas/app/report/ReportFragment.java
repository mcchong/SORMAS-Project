package de.symeda.sormas.app.report;


import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import com.google.android.gms.analytics.Tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.symeda.sormas.api.user.UserRole;
import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.app.BaseReportFragment;
import de.symeda.sormas.app.R;
import de.symeda.sormas.app.SormasApplication;
import de.symeda.sormas.app.backend.common.DaoException;
import de.symeda.sormas.app.backend.common.DatabaseHelper;
import de.symeda.sormas.app.backend.config.ConfigProvider;
import de.symeda.sormas.app.backend.report.WeeklyReport;
import de.symeda.sormas.app.backend.report.WeeklyReportEntry;
import de.symeda.sormas.app.backend.user.User;
import de.symeda.sormas.app.component.Item;
import de.symeda.sormas.app.component.controls.ControlPropertyField;
import de.symeda.sormas.app.component.controls.ControlSpinnerField;
import de.symeda.sormas.app.component.VisualState;
import de.symeda.sormas.app.component.controls.ValueChangeListener;
import de.symeda.sormas.app.core.BoolResult;
import de.symeda.sormas.app.core.Callback;
import de.symeda.sormas.app.core.IEntryItemOnClickListener;
import de.symeda.sormas.app.core.NotificationContext;
import de.symeda.sormas.app.core.async.DefaultAsyncTask;
import de.symeda.sormas.app.core.async.ITaskResultCallback;
import de.symeda.sormas.app.core.async.ITaskResultHolderIterator;
import de.symeda.sormas.app.core.async.TaskResultHolder;
import de.symeda.sormas.app.core.notification.NotificationHelper;
import de.symeda.sormas.app.core.notification.NotificationType;
import de.symeda.sormas.app.databinding.FragmentReportWeeklyLayoutBinding;
import de.symeda.sormas.app.report.adapter.PendingReportAdapter;
import de.symeda.sormas.app.report.adapter.WeeklyReportAdapter;
import de.symeda.sormas.app.report.adapter.WeeklyReportOverviewAdapter;
import de.symeda.sormas.app.report.viewmodel.PendingReportViewModel;
import de.symeda.sormas.app.report.viewmodel.ReportFilterOption;
import de.symeda.sormas.app.report.viewmodel.ReportFilterViewModel;
import de.symeda.sormas.app.report.viewmodel.WeeklyReportOverviewViewModel;
import de.symeda.sormas.app.report.viewmodel.WeeklyReportViewModel;
import de.symeda.sormas.app.rest.RetroProvider;
import de.symeda.sormas.app.rest.SynchronizeDataAsync;
import de.symeda.sormas.app.util.CharSequenceHelper;
import de.symeda.sormas.app.util.DataUtils;
import de.symeda.sormas.app.util.ErrorReportingHelper;
import de.symeda.sormas.app.util.NavigationHelper;
import de.symeda.sormas.app.util.SyncCallback;

/**
 * Created by Orson on 24/04/2018.
 * <p>
 * www.technologyboard.org
 * sampson.orson@gmail.com
 * sampson.orson@technologyboard.org
 */
public class ReportFragment extends BaseReportFragment<FragmentReportWeeklyLayoutBinding, WeeklyReport> {

    private AsyncTask onConfirmReportTask;
    private AsyncTask onWeeklyReportTask;
    private AsyncTask onPendingReportTask;
    private AsyncTask onWeeklyReportOverviewTask;
    private Tracker mTracker;
    private ReportFilterViewModel mReportFilter = new ReportFilterViewModel();
    private int mReportFilterLastCheckedId;
    private List<Item> mYearList;
    private List<Item> mEpiWeeksList;
    private WeeklyReport mWeeklyReport;
    private EpiWeekCategoryFactory mEpiWeekCategoryFactory = new EpiWeekCategoryFactory();

    private LinearLayoutManager mLinearLayoutManager;
    private WeeklyReportAdapter mWeeklyReportAdapter;
    private PendingReportAdapter mPendingReportAdapter;
    private WeeklyReportOverviewAdapter mWeeklyReportOverviewAdapter;

    private User mUser;
    private String mReportDate = "";

    private IEntryItemOnClickListener onAddMissingCase;
    private IEntryItemOnClickListener onConfirmReport;

    @Override
    protected String getSubHeadingTitle() {
        Resources r = getResources();
        String defaultValue = r.getString(R.string.hint_report_not_submitted);
        String format = !mUser.hasUserRole(UserRole.INFORMANT) ? r.getString(R.string.caption_report_date) : r.getString(R.string.caption_confirmation_date);

        return CharSequenceHelper.italic(String.format(format, mReportDate == null || mReportDate.isEmpty() ? defaultValue : mReportDate)).toString();
    }

    @Override
    protected boolean onBeforeLayoutBinding(Bundle savedInstanceState, TaskResultHolder resultHolder, BoolResult resultStatus, boolean executionComplete) {
        if (!executionComplete) {
            //WeeklyReport task = queryRootEntity();

            resultHolder.forOther().add(DataUtils.toItems(DateHelper.getYearsToNow()));
            resultHolder.forOther().add(DataUtils.toItems(DateHelper.createIntegerEpiWeeksList(mReportFilter.getYear())));
        } else {
            ITaskResultHolderIterator otherIterator = resultHolder.forOther().iterator();

            if (otherIterator.hasNext())
                mYearList = otherIterator.next();

            if (otherIterator.hasNext())
                mEpiWeeksList = otherIterator.next();

            mLinearLayoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false);
            mUser = ConfigProvider.getUser();
            SormasApplication application = (SormasApplication) getActivity().getApplication();
            mTracker = application.getDefaultTracker();

            setupCallback();
        }

        return true;
    }

    @Override
    protected void onLayoutBinding(FragmentReportWeeklyLayoutBinding contentBinding) {
        contentBinding.setData(mWeeklyReport);
        contentBinding.setReportFilter(mReportFilter);
        contentBinding.setReportFilterOptionClass(ReportFilterOption.class);
    }

    @Override
    protected void onAfterLayoutBinding(final FragmentReportWeeklyLayoutBinding contentBinding) {

        contentBinding.weeklyReportYear.initializeSpinner(mYearList, mReportFilter.getYear(), new ValueChangeListener() {
            @Override
            public void onChange(ControlPropertyField field) {
                Integer year = (Integer) field.getValue();
                if (year != null) {
                    contentBinding.weeklyReportEpiWeek.setSpinnerData(DataUtils.toItems(DateHelper.createIntegerEpiWeeksList(year)));
                } else {
                    contentBinding.weeklyReportEpiWeek.setSpinnerData(null);
                }
            }
        });


        contentBinding.weeklyReportEpiWeek.initializeSpinner(mEpiWeeksList, mReportFilter.getWeek(), new ValueChangeListener() {
            @Override
            public void onChange(ControlPropertyField field) {
                Integer selectedEpiWeek = (Integer) field.getValue();

                if (selectedEpiWeek != null) {
                    mReportFilter.setYear((int) getContentBinding().weeklyReportYear.getValue());
                    mReportFilter.setWeek((int) getContentBinding().weeklyReportEpiWeek.getValue());

                    if (mUser.hasUserRole(UserRole.INFORMANT)) {
                        mWeeklyReport = DatabaseHelper.getWeeklyReportDao().queryForEpiWeek(mReportFilter.getEpiWeek(), mUser);

                        BaseEpiWeekCategory category = mEpiWeekCategoryFactory.getEpiWeekCategory(mReportFilter.getEpiWeek());
                        category.processReport(mReportFilter.getEpiWeek(), mUser, new Callback.IAction2<String, BaseEpiWeekCategory>() {
                            @Override
                            public void call(String reportDate, BaseEpiWeekCategory c) {
                                showPendingReport(c);
                                showWeeklyReport(c);

                                if (c.showShowReportNotSubmittedNotification()) {
                                    NotificationHelper.showNotification((NotificationContext) ReportFragment.this, NotificationType.WARNING, R.string.hint_report_not_submitted);
                                }

                                getContentBinding().noWeeklyReportHint.setVisibility(c.showNoReportNotification() ? View.VISIBLE : View.GONE);
                                getContentBinding().noWeeklyReportData.setVisibility(c.showNoDataNotification() ? View.VISIBLE : View.GONE);
                                getContentBinding().addMissingCase.setVisibility(c.showAddMissingButton() ? View.VISIBLE : View.GONE);
                                getContentBinding().confirmReport.setVisibility(c.showConfirmButton() ? View.VISIBLE : View.GONE);

                                mReportDate = reportDate;
                                updateUI();
                            }
                        });
                    } else {
                        hideReportControls();

                        showWeeklyReportOverview();
                        updateUI();
                    }
                } else {
                    mReportDate = "";
                    mReportFilter.setYear(null);
                    mReportFilter.setWeek(null);
                    updateUI();
                }
            }
        });
    }


    private void showPendingReport(final BaseEpiWeekCategory c) {
        getContentBinding().reportContentFrame.setVisibility(c.showReportTable() ? View.VISIBLE : View.GONE);

        if (!c.showReportTable())
            return;

        if (!c.showPendingReport())
            return;

        if (mUser == null)
            return;

        if (mUser.hasUserRole(UserRole.INFORMANT)) {
            DefaultAsyncTask executor = new DefaultAsyncTask(getContext()) {
                private String saveUnsuccessful;

                @Override
                public void onPreExecute() {
                    //getBaseActivity().showPreloader();
                    //
                    changeButtonsEnabledStatus(false);
                }

                @Override
                public void doInBackground(TaskResultHolder resultHolder) {
                    List<PendingReportViewModel> list = new ArrayList<>();
                    for (WeeklyReportEntry entry : DatabaseHelper.getWeeklyReportEntryDao().getAllByWeeklyReport(c.getReport())) {
                        list.add(new PendingReportViewModel(entry.getDisease(), entry.getNumberOfCases()));
                    }

                    resultHolder.forOther().add(list);
                }
            };
            onPendingReportTask = executor.execute(new ITaskResultCallback() {
                @Override
                public void taskResult(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    //getBaseActivity().hidePreloader();
                    //getBaseActivity().showFragmentView();

                    changeButtonsEnabledStatus(true);

                    if (resultHolder == null) {
                        return;
                    }

                    List<PendingReportViewModel> list = new ArrayList<>();
                    ITaskResultHolderIterator otherIterator = resultHolder.forOther().iterator();

                    if (otherIterator.hasNext())
                        list = otherIterator.next();

                    mPendingReportAdapter = new PendingReportAdapter(ReportFragment.this.getActivity(), R.layout.row_pending_report_list_item_layout, list);
                    getContentBinding().recyclerViewForList.setLayoutManager(mLinearLayoutManager);
                    getContentBinding().recyclerViewForList.setAdapter(mPendingReportAdapter);
                    mPendingReportAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void showWeeklyReport(final BaseEpiWeekCategory c) {
        getContentBinding().reportContentFrame.setVisibility(c.showReportTable() ? View.VISIBLE : View.GONE);

        if (!c.showReportTable())
            return;

        if (!c.showWeeklyReport())
            return;

        if (mUser == null)
            return;

        if (mUser.hasUserRole(UserRole.INFORMANT)) {
            try {
                DefaultAsyncTask executor = new DefaultAsyncTask(getContext()) {
                    private String saveUnsuccessful;

                    @Override
                    public void onPreExecute() {
                        //getBaseActivity().showPreloader();
                        //
                        changeButtonsEnabledStatus(false);
                    }

                    @Override
                    public void doInBackground(TaskResultHolder resultHolder) {
                        List<WeeklyReportViewModel> list = new ArrayList<>();
                        for (WeeklyReportEntry entry : DatabaseHelper.getWeeklyReportEntryDao().getAllByWeeklyReport(c.getReport())) {
                            list.add(new WeeklyReportViewModel(entry.getDisease(), entry.getNumberOfCases()));
                        }

                        resultHolder.forOther().add(list);
                    }
                };
                onWeeklyReportTask = executor.execute(new ITaskResultCallback() {
                    @Override
                    public void taskResult(BoolResult resultStatus, TaskResultHolder resultHolder) {
                        //getBaseActivity().hidePreloader();
                        //getBaseActivity().showFragmentView();

                        changeButtonsEnabledStatus(true);

                        if (resultHolder == null) {
                            return;
                        }

                        List<WeeklyReportViewModel> list = new ArrayList<>();
                        ITaskResultHolderIterator otherIterator = resultHolder.forOther().iterator();

                        if (otherIterator.hasNext())
                            list = otherIterator.next();

                        mWeeklyReportAdapter = new WeeklyReportAdapter(ReportFragment.this.getActivity(), R.layout.row_weekly_report_list_item_layout, list);
                        getContentBinding().recyclerViewForList.setLayoutManager(mLinearLayoutManager);
                        getContentBinding().recyclerViewForList.setAdapter(mWeeklyReportAdapter);
                        mWeeklyReportAdapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception ex) {
                //getBaseActivity().hidePreloader();
                //getBaseActivity().showFragmentView();
            }
        }

    }

    private void showWeeklyReportOverview() {
        if (mReportFilter == null)
            return;

        if (mUser == null)
            return;

        try {
            DefaultAsyncTask executor = new DefaultAsyncTask(getContext()) {
                private String saveUnsuccessful;

                @Override
                public void onPreExecute() {
                    //getBaseActivity().showPreloader();
                    //
                    changeButtonsEnabledStatus(false);
                }

                @Override
                public void doInBackground(TaskResultHolder resultHolder) {
                    List<WeeklyReportOverviewViewModel> list = new ArrayList<>();
                    List<User> informants = DatabaseHelper.getUserDao().getByDistrictAndRole(mUser.getDistrict(), UserRole.INFORMANT, User.HEALTH_FACILITY + "_id");
                    for (User informant : informants) {
                        WeeklyReport report = DatabaseHelper.getWeeklyReportDao().queryForEpiWeek(mReportFilter.getEpiWeek(), informant);
                        int numberOfCases = DatabaseHelper.getCaseDao().getNumberOfCasesForEpiWeek(mReportFilter.getEpiWeek(), informant);
                        list.add(new WeeklyReportOverviewViewModel(informant.getHealthFacility(), informant, UserRole.INFORMANT, numberOfCases, report != null));
                    }

                    resultHolder.forOther().add(list);
                }
            };
            onWeeklyReportOverviewTask = executor.execute(new ITaskResultCallback() {
                @Override
                public void taskResult(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    //getBaseActivity().hidePreloader();
                    //getBaseActivity().showFragmentView();

                    changeButtonsEnabledStatus(true);

                    if (resultHolder == null) {
                        return;
                    }

                    List<WeeklyReportOverviewViewModel> list = new ArrayList<>();
                    ITaskResultHolderIterator otherIterator = resultHolder.forOther().iterator();

                    if (otherIterator.hasNext())
                        list = otherIterator.next();

                    mWeeklyReportOverviewAdapter = new WeeklyReportOverviewAdapter(ReportFragment.this.getActivity(), R.layout.row_weekly_report_overview_list_item_layout, list);
                    getContentBinding().recyclerViewForList.setLayoutManager(mLinearLayoutManager);
                    getContentBinding().recyclerViewForList.setAdapter(mWeeklyReportOverviewAdapter);
                    mWeeklyReportOverviewAdapter.notifyDataSetChanged();
                }
            });
        } catch (Exception ex) {
            //getBaseActivity().hidePreloader();
            //getBaseActivity().showFragmentView();
        }
    }

    private void hideReportControls() {
        getContentBinding().reportContentFrame.setVisibility(View.VISIBLE);
        getContentBinding().noWeeklyReportHint.setVisibility(View.GONE);
        getContentBinding().noWeeklyReportData.setVisibility(View.GONE);
        getContentBinding().addMissingCase.setVisibility(View.GONE);
        getContentBinding().confirmReport.setVisibility(View.GONE);
    }

    @Override
    protected void updateUI(FragmentReportWeeklyLayoutBinding contentBinding, WeeklyReport weeklyReport) {
        contentBinding.weeklyReportStartDate.setValue(mReportFilter.getStartDate());
        contentBinding.weeklyReportEndDate.setValue(mReportFilter.getEndDate());

        getSubHeadingHandler().updateSubHeadingTitle();
    }

    @Override
    protected WeeklyReport getPrimaryData() {
        return mWeeklyReport;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_report_weekly_layout;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void changeButtonsEnabledStatus(boolean status) {
        getContentBinding().addMissingCase.setEnabled(status);
        getContentBinding().confirmReport.setEnabled(status);
    }

    private void setupCallback() {
//        mOnReportFilterChangeCallback = new OnTeboSwitchCheckedChangeListener() {
//            @Override
//            public void onCheckedChanged(ControlSwitchField teboSwitch, Object checkedItem, int checkedId) {
//                if (checkedId < 0)
//                    return;
//
//                if (mReportFilterLastCheckedId == checkedId) {
//                    return;
//                }
//
//                mReportFilterLastCheckedId = checkedId;
//
//                ReportFilterOption answer = (ReportFilterOption)checkedItem;
//
//                if (answer == ReportFilterOption.SPECIFY_WEEK) {
//                    getContentBinding().specifyReportFilterFrame.setVisibility(View.VISIBLE);
//                } else {
//                    getContentBinding().specifyReportFilterFrame.setVisibility(View.GONE);
//                }
//
//                if (answer == ReportFilterOption.LAST_WEEK) {
//                    EpiWeek epiWeek = DateHelper.getPreviousEpiWeek(new Date());
//
//                    mReportFilter.setYear(epiWeek.getYear());
//                    mReportFilter.setWeek(epiWeek.getWeek());
//
//                    getContentBinding().weeklyReportYear.notifyDataChanged();
//                    //requestLayoutRebind();
//                    updateUI();
//                }
//
//                if (answer == ReportFilterOption.THIS_WEEK) {
//                    EpiWeek epiWeek = DateHelper.getEpiWeek(new Date());
//
//                    mReportFilter.setYear(epiWeek.getYear());
//                    mReportFilter.setWeek(epiWeek.getWeek());
//
//                    getContentBinding().weeklyReportYear.notifyDataChanged();
//                    //requestLayoutRebind();
//                    updateUI();
//                }
//            }
//        };

        onAddMissingCase = new IEntryItemOnClickListener() {
            @Override
            public void onClick(View v, Object item) {
                NavigationHelper.gotoNewCase(getActivity());
            }
        };

        onConfirmReport = new IEntryItemOnClickListener() {
            @Override
            public void onClick(View v, Object item) {


                try {
                    DefaultAsyncTask executor = new DefaultAsyncTask(getContext()) {
                        private String saveUnsuccessful;

                        @Override
                        public void onPreExecute() {
                            //getBaseActivity().showPreloader();
                            //
                            changeButtonsEnabledStatus(false);

                            saveUnsuccessful = getActivity().getString(R.string.snackbar_weekly_report_error);
                        }

                        @Override
                        public void doInBackground(TaskResultHolder resultHolder) {
                            try {
                                DatabaseHelper.getWeeklyReportDao().create(mReportFilter.getEpiWeek());
                            } catch (DaoException e) {
                                Log.e(getClass().getName(), "Error while trying to create weekly report", e);
                                Log.e(getClass().getName(), "- root cause: ", ErrorReportingHelper.getRootCause(e));
                                resultHolder.setResultStatus(new BoolResult(false, saveUnsuccessful));
                                ErrorReportingHelper.sendCaughtException(mTracker, e, null, true);
                            }
                        }
                    };
                    onConfirmReportTask = executor.execute(new ITaskResultCallback() {
                        @Override
                        public void taskResult(BoolResult resultStatus, TaskResultHolder resultHolder) {
                            //getBaseActivity().hidePreloader();
                            //getBaseActivity().showFragmentView();

                            changeButtonsEnabledStatus(true);

                            if (resultHolder == null) {
                                return;
                            }

                            if (!resultStatus.isSuccess()) {
                                NotificationHelper.showNotification((NotificationContext) getActivity(), NotificationType.ERROR, resultStatus.getMessage());
                                return;
                            }

                            if (RetroProvider.isConnected()) {
                                SynchronizeDataAsync.callWithProgressDialog(SynchronizeDataAsync.SyncMode.Changes, getActivity(), new SyncCallback() {
                                    @Override
                                    public void call(boolean syncFailed, String syncFailedMessage) {
                                        if (syncFailed) {
                                            NotificationHelper.showNotification((NotificationContext) getActivity(), NotificationType.SUCCESS, R.string.snackbar_weekly_report_sync_confirmed);
                                        } else {
                                            NotificationHelper.showNotification((NotificationContext) getActivity(), NotificationType.WARNING, R.string.snackbar_weekly_report_confirmed);
                                        }
                                        reloadFragment();
                                    }
                                });
                            } else {
                                NotificationHelper.showNotification((NotificationContext) getActivity(), NotificationType.WARNING, R.string.snackbar_weekly_report_confirmed);
                                reloadFragment();
                            }
                        }
                    });
                } catch (Exception ex) {
                    //getBaseActivity().hidePreloader();
                    //getBaseActivity().showFragmentView();
                }
            }
        };
    }

    public static ReportFragment newInstance() {
        return newInstance(ReportFragment.class);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (onConfirmReportTask != null && !onConfirmReportTask.isCancelled())
            onConfirmReportTask.cancel(true);

        if (onWeeklyReportTask != null && !onWeeklyReportTask.isCancelled())
            onWeeklyReportTask.cancel(true);

        if (onPendingReportTask != null && !onPendingReportTask.isCancelled())
            onPendingReportTask.cancel(true);

        if (onWeeklyReportOverviewTask != null && !onWeeklyReportOverviewTask.isCancelled())
            onWeeklyReportOverviewTask.cancel(true);
    }

}
