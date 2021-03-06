package gov.hhs.usas.dss.model;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:report.properties")
public class Vacancy extends Report {
	
	@Value("${vac.search.path}") private String searchPath;
	@Value("${vac.intg.type}") private String intgType;
	@Value("${vac.file.name}") private String fileName;
	@Value("${vac.report.name}") private String reportName;
	@Value("${vac.parm.num}") private int parmNum;
	@Value("${vac.svp.name}") private String svpName;
	@Value("${vac.svp.useval}") private String svpUseval;
	@Value("${vac.svp.display}") private String svpDisplay;
	@Value("${vac.rvp.name}") private String rvpName;
	@Value("${vac.rpt.iteration}") private int rptIteration; 
	@Value("${vac.date.interval}") private int dateInterval;
	@Value("${vac.end.date}") private String endDate;
	@Value("${vac.truncate}") private String spTruncate;
	@Value("${vac.run.report}") private boolean runReport;

	public Vacancy() {

	}
	
	@PostConstruct
	public void construct() {
		super.setSearchPath(searchPath);
		super.setIntgType(intgType);
		super.setFileName(fileName);
		super.setReportName(reportName);
		super.setParmNum(parmNum);
		super.setSvpName(svpName);
		super.setSvpUseval(svpUseval);
		super.setSvpDisplay(svpDisplay);
		super.setRvpName(rvpName);
		super.setRptIteration(rptIteration);
		super.setDateInterval(dateInterval);
		super.setEndDate(endDate);
		super.setSpTruncate(spTruncate);
		super.setRunReport(runReport);
	}
	
}
