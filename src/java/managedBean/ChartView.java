/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package managedBean;

/**
 *
 * @author 曹锡鹏
 */
import entity.Crossing;
import entity.TrafficFlow;
import jsf.util.JsfUtil;
import jsf.util.JsfUtil.PersistAction;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.inject.Inject;
import javax.inject.Named;
import jsf.CrossingController;
import org.primefaces.event.ItemSelectEvent;
import org.primefaces.model.chart.Axis;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.BarChartModel;
import org.primefaces.model.chart.CategoryAxis;
import org.primefaces.model.chart.LineChartModel;
import org.primefaces.model.chart.ChartSeries;
import org.primefaces.model.chart.LineChartSeries;

@Named("ChartView")
@ManagedBean
public class ChartView implements Serializable {

    private LineChartModel lineModel;
    private BarChartModel barModel;
    private ChartSeries crossings = new ChartSeries();
    private Date selectDate = (Date) FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get("selectDate");
    private String selectDanwei = (String) FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get("selectDanwei");
    private Crossing selectedCrossing = (Crossing) FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get("selectCrossing");
    private DateFormat dateFormat_month = new SimpleDateFormat("yyyy-MM");
    private DateFormat dateFormat_day = new SimpleDateFormat("yyyy-MM-dd");
    private DateFormat dateFormat_hour = new SimpleDateFormat("yyyy-MM-dd HH");
    private DateFormat dateFormat_minute = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @PostConstruct
    public void init() {
        createLineModel();
        createBarModel();
    }
    @EJB
    private session.CrossingFacade crossingFacade;
    @EJB
    private session.TrafficFlowFacade trafficFlowFacade;

    public LineChartModel getLineModel() {
        return lineModel;
    }

    public BarChartModel getBarModel() {
        return barModel;
    }

    private void createLineModel() {
        lineModel = initCategoryModel();
        lineModel.setLegendPosition("e");
        lineModel.setAnimate(true);
        lineModel.setShowPointLabels(true);
        lineModel.getAxes().put(AxisType.X, new CategoryAxis("时间段"));
        Axis yAxis = lineModel.getAxis(AxisType.Y);
        yAxis.setLabel("数量(十万辆)");
        yAxis.setMin(0);
        yAxis.setMax(200);
        Axis xAxis = lineModel.getAxis(AxisType.X);
        xAxis.setTickAngle(-30);
    }

    private LineChartModel initCategoryModel() {
        LineChartModel model = new LineChartModel();
        ChartSeries line = new ChartSeries();
        line.setLabel("车辆数");

        if (selectedCrossing == null && selectDate == null) {
            Date currentDate = new Date();
            String currentDateString = dateFormat_day.format(currentDate);
            String startDateString = dateFormat_month.format(currentDate).concat("-01");
            List<String> datesList = collectLocalDates(LocalDate.parse(startDateString), LocalDate.parse(currentDateString));
            Iterator datesIterator = datesList.iterator();
            String formatDate;
            Crossing c = crossingFacade.findAll().get(0);
            Collection<TrafficFlow> tfs = c.getTrafficFlowCollection();

            while (datesIterator.hasNext()) {
                String dateString = (String) datesIterator.next();
                long avg = 0;
                Iterator it = tfs.iterator();
                while (it.hasNext()) {
                    TrafficFlow tf = (TrafficFlow) it.next();
                    formatDate = dateFormat_day.format(tf.getTime());
                    if (formatDate.equals(dateString)) {
                        avg = avg + (tf.getCrossingE() + tf.getCrossingN() + tf.getCrossingS() + tf.getCrossingW()) / 4;
                    }
                }
                if (avg != 0) {
                    line.set(dateString, avg);
                }
            }
        } else if (selectedCrossing == null && selectDanwei.equals("天") && selectDate != null) {
            String selectDateString = dateFormat_day.format(selectDate);
            String formatDate_day = new String();
            String endDateString = dateFormat_day.format(selectDate);
            String startDateString = dateFormat_month.format(selectDate).concat("-01");
            List<String> datesList = collectLocalDates(LocalDate.parse(startDateString), LocalDate.parse(endDateString));
            Iterator datesIterator = datesList.iterator();
            Crossing c = crossingFacade.findAll().get(0);
            Collection<TrafficFlow> tfs = c.getTrafficFlowCollection();
            while (datesIterator.hasNext()) {
                String dateString = (String) datesIterator.next();
                Iterator it = tfs.iterator();
                long avg = 0;
                while (it.hasNext()) {
                    TrafficFlow tf = (TrafficFlow) it.next();
                    formatDate_day = dateFormat_day.format(tf.getTime());
                    if (formatDate_day.equals(dateString)) {
                        avg = (avg + (tf.getCrossingE() + tf.getCrossingN() + tf.getCrossingS() + tf.getCrossingW()) / 4) / 2;
                    }
                }
                if (avg != 0) {
                    line.set(dateString, avg);
                }
            }
        }

        model.addSeries(line);

        return model;
    }

    private void createBarModel() {
        barModel = initBarModel();
        barModel.setLegendPosition("e");
        barModel.setAnimate(true);
        Axis xAxis = barModel.getAxis(AxisType.X);
        xAxis.setLabel("路口");

        Axis yAxis = barModel.getAxis(AxisType.Y);
        yAxis.setLabel("数量(十万辆)");
        yAxis.setMin(0);
        yAxis.setMax(200);
    }

    private BarChartModel initBarModel() {
        BarChartModel model = new BarChartModel();

        crossings.setLabel("车辆数");
        List<Crossing> crossingList = crossingFacade.findAll();
        Iterator iterator = crossingList.iterator();
        long avg=0;
        while (iterator.hasNext()) {
            Crossing c = (Crossing) iterator.next();
            Collection<TrafficFlow> trafficFlows = c.getTrafficFlowCollection();
            if(trafficFlows.isEmpty())
            {
                continue;
            }
            Iterator iterator1 = trafficFlows.iterator();
            long sum = 0;
            int i = 0;
            while (iterator1.hasNext()) {
                TrafficFlow t = (TrafficFlow) iterator1.next();
                sum = sum + t.getCrossingE() + t.getCrossingN() + t.getCrossingS() + t.getCrossingW();
                i++;
            }
            avg = sum / (4 * i);
            crossings.set(c.getLocation(), avg);
        }
        model.addSeries(crossings);

        return model;
    }

    public void itemSelect(ItemSelectEvent event) {
        Map datasMap = crossings.getData();
        Iterator it = datasMap.entrySet().iterator();
        String key = "";
        for (int i = 0; i <= event.getItemIndex(); i++) {
            Map.Entry mapentry = (Map.Entry) it.next();
            key = mapentry.getKey().toString();
        }
        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "选择路口:", key);
        FacesContext.getCurrentInstance().addMessage(null, msg);
        List<Crossing> crossingList=crossingFacade.findAll();
        it=crossingList.iterator();
        while (it.hasNext()) {
            Crossing c=(Crossing) it.next();
            if(c.getLocation().equals(key)){
                selectedCrossing=c;
            } 
        }
        FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put("selectCrossing", selectedCrossing);
    }

    public static List<String> collectLocalDates(LocalDate start, LocalDate end) {
        // 用起始时间作为流的源头，按照每次加一天的方式创建一个无限流
        return Stream.iterate(start, localDate -> localDate.plusDays(1))
                // 截断无限流，长度为起始时间和结束时间的差+1个
                .limit(ChronoUnit.DAYS.between(start, end) + 1)
                // 由于最后要的是字符串，所以map转换一下
                .map(LocalDate::toString)
                // 把流收集为List
                .collect(Collectors.toList());
    }
}
