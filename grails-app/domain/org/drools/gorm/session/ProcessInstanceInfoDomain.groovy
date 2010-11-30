package org.drools.gorm.session

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Blob;
import java.util.Arrays;
import java.util.Date;

import org.drools.common.InternalKnowledgeRuntime;
import org.drools.common.InternalRuleBase;
import org.drools.gorm.DomainUtils;
import org.drools.impl.InternalKnowledgeBase;
import org.drools.impl.StatefulKnowledgeSessionImpl;
import org.drools.marshalling.impl.MarshallerReaderContext;
import org.drools.marshalling.impl.MarshallerWriteContext;
import org.drools.marshalling.impl.ProcessInstanceMarshaller;
import org.drools.marshalling.impl.ProcessMarshallerRegistry;
import org.drools.process.instance.ProcessInstance;
import org.drools.process.instance.impl.ProcessInstanceImpl;
import org.drools.runtime.Environment;
import org.hibernate.Hibernate;

class ProcessInstanceInfoDomain implements ProcessInstanceInfo {    
    long id
    String processId
    Date startDate = new Date()
    Date lastReadDate
    Date lastModificationDate
    int state
    Blob processInstanceBlob
    
    ProcessInstance processInstance
    Environment env
    
    static hasMany = [ eventTypes : ProcessInstanceInfoEventTypeDomain ]
    
    static constraints = {
        lastModificationDate(nullable:true)
        lastReadDate(nullable:true)
        processInstanceBlob(nullable:true)
    }  
    
    static transients = ['processInstance', 'MarshallerFromContext', 'env',
        'ProcessInstanceId', 'processInstanceByteArray']
    
    static mapping = {
        processInstanceBlob type: 'blob'
    }
    
    def long getId() {
        return id
    }
    
    def getProcessInstanceByteArray() {
        if (processInstanceBlob) {
            return DomainUtils.blobToByteArray(processInstanceBlob)
        }
    }
    
    def setProcessInstanceByteArray(value) {
        this.setProcessInstanceBlob(Hibernate.createBlob(value))
    }    
    
    def getProcessInstanceId() {
        return id
    }
    
    public void updateLastReadDate() {
        lastReadDate = new Date()
    }
    
    def ProcessInstance getProcessInstance(InternalKnowledgeRuntime kruntime, Environment env) {
        this.env = env;
        if ( processInstance == null ) {
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream( processInstanceByteArray );
                MarshallerReaderContext context = new MarshallerReaderContext( bais,
                        (InternalRuleBase) ((InternalKnowledgeBase) kruntime.getKnowledgeBase()).getRuleBase(),
                        null,
                        null,
                        this.env
                        );
                ProcessInstanceMarshaller marshaller = getMarshallerFromContext( context );
                context.wm = ((StatefulKnowledgeSessionImpl) kruntime).getInternalWorkingMemory();
                processInstance = marshaller.readProcessInstance(context);
                context.close();
            } catch ( IOException e ) {
                throw new IllegalStateException( "IOException while loading process instance: ", e );
            }
        }
        return processInstance;
    }
    
    private ProcessInstanceMarshaller getMarshallerFromContext(MarshallerReaderContext context) throws IOException {
        String processInstanceType = context.stream.readUTF()
        return ProcessMarshallerRegistry.INSTANCE.getMarshaller(processInstanceType)
    }
    
    private void saveProcessInstanceType(MarshallerWriteContext context, 
    ProcessInstance processInstance, String processInstanceType)
    throws IOException {
        // saves the processInstance type first
        context.stream.writeUTF(processInstanceType)
    }
    
    public void beforeUpdate() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean variablesChanged = false;
        try {
            MarshallerWriteContext context = new MarshallerWriteContext( baos,
                    null,
                    null,
                    null,
                    null,
                    this.env );
            String processType = ((ProcessInstanceImpl) processInstance).getProcess().getType();
            saveProcessInstanceType( context,
                    processInstance,
                    processType );
            ProcessInstanceMarshaller marshaller = ProcessMarshallerRegistry.INSTANCE.getMarshaller( processType );
            marshaller.writeProcessInstance( context,
                    processInstance);
            context.close();
        } catch ( IOException e ) {
            throw new IllegalStateException( "IOException while storing process instance " + processInstance.getId(), e);
        }
        byte[] newByteArray = baos.toByteArray();
        if ( variablesChanged || 
        !Arrays.equals( newByteArray, processInstanceByteArray ) ) {
            this.state = processInstance.getState();
            this.lastModificationDate = new Date();
            this.processInstanceByteArray = newByteArray;
            if (this.eventTypes) {
                this.eventTypes.clear();
            }
            for ( String type : processInstance.getEventTypes() ) {
                eventTypes.add( type );
            }
        }
    }
}
