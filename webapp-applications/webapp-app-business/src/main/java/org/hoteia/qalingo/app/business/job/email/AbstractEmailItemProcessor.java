/**
 * Most of the code in the Qalingo project is copyrighted Hoteia and licensed
 * under the Apache License Version 2.0 (release version 0.8.0)
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *                   Copyright (c) Hoteia, 2012-2014
 * http://www.hoteia.com - http://twitter.com/hoteia - contact@hoteia.com
 *
 */
package org.hoteia.qalingo.app.business.job.email;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Blob;

import org.apache.commons.lang.StringUtils;
import org.hoteia.qalingo.core.Constants;
import org.hoteia.qalingo.core.batch.CommonProcessIndicatorItemWrapper;
import org.hoteia.qalingo.core.domain.Email;
import org.hoteia.qalingo.core.service.EmailService;
import org.hoteia.qalingo.core.util.impl.MimeMessagePreparatorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.Assert;


/**
 * 
 */
public abstract class AbstractEmailItemProcessor<T> implements ItemProcessor<CommonProcessIndicatorItemWrapper<Long, Email>, Email>, InitializingBean {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private JavaMailSender mailSender;
	
	protected EmailService emailService;

	public final void afterPropertiesSet() throws Exception {
		Assert.notNull(mailSender, "You must provide a JavaMailSender.");
		Assert.notNull(emailService, "You must provide an EmailService.");
	}

	public Email process(CommonProcessIndicatorItemWrapper<Long, Email> wrapper) throws Exception {
		Email email = wrapper.getItem();
		Blob emailcontent = email.getEmailContent();
		
		InputStream is = emailcontent.getBinaryStream();
	    ObjectInputStream oip = new ObjectInputStream(is);
	    Object object = oip.readObject();
	    
	    MimeMessagePreparatorImpl mimeMessagePreparator = (MimeMessagePreparatorImpl) object;
	    
	    oip.close();
	    is.close();

	    try {
	    	// SANITY CHECK
	    	if(email.getStatus().equals(Email.EMAIl_STATUS_PENDING)){
	    		
	    		if (mimeMessagePreparator.isMirroringActivated()) {
	    			String filePathToSave = mimeMessagePreparator.getMirroringFilePath();
	                File file = new File(filePathToSave);
	                
	                // SANITY CHECK : create folders
                	String absoluteFolderPath = file.getParent();
                	File absolutePathFile = new File(absoluteFolderPath);
                	if(!absolutePathFile.exists()){
                		absolutePathFile.mkdirs();
                	}
                	
	                if(!file.exists()){
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, Constants.UTF8);
                        Writer out = new BufferedWriter(outputStreamWriter);
                        if(StringUtils.isNotEmpty(mimeMessagePreparator.getHtmlContent())) {
	                        out.write(mimeMessagePreparator.getHtmlContent());
                        } else {
	                        out.write(mimeMessagePreparator.getPlainTextContent());
                        }
                        
                        try {
                            if (out != null){
                                out.close();
                            }
                        } catch (IOException e) {
                            logger.debug("Cannot close the file", e);
                        }
	                } else {
	                    logger.debug("File already exists : " + filePathToSave);
	                }
	            }
	    		
				mailSender.send(mimeMessagePreparator);
				email.setStatus(Email.EMAIl_STATUS_SENDED);
	    	} else {
	    		logger.warn("Batch try to send email was already sended!");
	    	}
            
        } catch (Exception e) {
        	logger.error("Fail to send email! Exception is save in database, id:" + email.getId());
    		email.setStatus(Email.EMAIl_STATUS_ERROR);
    		emailService.handleEmailException(email, e);
        }

	    return email;
    }
	
	public void setMailSender(JavaMailSender mailSender) {
	    this.mailSender = mailSender;
    }

	public void setEmailService(EmailService emailService) {
	    this.emailService = emailService;
    }
	
}