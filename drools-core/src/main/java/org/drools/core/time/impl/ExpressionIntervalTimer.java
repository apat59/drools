/*
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.time.impl;

import org.drools.core.WorkingMemory;
import org.drools.core.base.mvel.MVELCompilationUnit;
import org.drools.core.base.mvel.MVELObjectExpression;
import org.drools.core.common.AgendaItem;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ScheduledAgendaItem;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.rule.ConditionalElement;
import org.drools.core.rule.Declaration;
import org.drools.core.spi.Activation;
import org.drools.core.time.TimeUtils;
import org.drools.core.time.Trigger;
import org.kie.api.runtime.Calendars;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;

public class ExpressionIntervalTimer  extends BaseTimer
    implements
    Timer,
    Externalizable {

    private Date startTime;

    private Date endTime;

    private int  repeatLimit;

    private MVELObjectExpression delay;
    private MVELObjectExpression period;

    public ExpressionIntervalTimer() {

    }



    public ExpressionIntervalTimer(Date startTime,
                                   Date endTime,
                                   int repeatLimit,
                                   MVELObjectExpression delay,
                                   MVELObjectExpression period) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.repeatLimit = repeatLimit;
        this.delay = delay;
        this.period = period;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( startTime );
        out.writeObject( endTime );
        out.writeInt( repeatLimit );
        out.writeObject( delay );
        out.writeObject( period );
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        this.startTime = (Date) in.readObject();
        this.endTime = (Date) in.readObject();
        this.repeatLimit = in.readInt();
        this.delay = (MVELObjectExpression) in.readObject();
        this.period = (MVELObjectExpression) in.readObject();
    }
    
    public MVELCompilationUnit getDelayMVELCompilationUnit() {
        return this.delay.getMVELCompilationUnit();
    }  
    
    public MVELCompilationUnit getPeriodMVELCompilationUnit() {
        return this.period.getMVELCompilationUnit();
    }       

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public MVELObjectExpression getDelay() {
        return delay;
    }

    public MVELObjectExpression getPeriod() {
        return period;
    }


    public Trigger createTrigger( Activation item, InternalWorkingMemory wm ) {

        long timestamp = ((InternalWorkingMemory) wm).getTimerService().getCurrentTime();
        String[] calendarNames = item.getRule().getCalendars();
        Calendars calendars = ((InternalWorkingMemory) wm).getCalendars();

        Declaration[] delayDeclrs = ((AgendaItem)item).getTerminalNode().getTimerDelayDeclarations();
        Declaration[] periodDeclrs = ((AgendaItem)item).getTerminalNode().getTimerPeriodDeclarations();
//
//        long timeSinceLastFire = 0;
        ScheduledAgendaItem schItem = ( ScheduledAgendaItem ) item;
        DefaultJobHandle jh = null;
        if ( schItem.getJobHandle() != null ) {
            jh = ( DefaultJobHandle) schItem.getJobHandle();
//            IntervalTrigger preTrig = ( IntervalTrigger ) jh.getTimerJobInstance().getTrigger();
//            if ( preTrig.hasNextFireTime() != null ) {
//                timeSinceLastFire = timestamp - preTrig.getLastFireTime().getTime();
//            }
        }
//
//
//        long newDelay = (delay  != null ? evalDelay( item.getTuple(), ((AgendaItem)item).getTerminalNode().getTimerDelayDeclarations(), wm ) : 0) - timeSinceLastFire;
//        if ( newDelay < 0 ) {
//            newDelay = 0;
//        }
//
//        return new IntervalTrigger( timestamp,
//                                    this.startTime,
//                                    this.endTime,
//                                    this.repeatLimit,
//                                    newDelay,
//                                    period != null ? evalPeriod( item.getTuple(), ((AgendaItem)item).getTerminalNode().getTimerPeriodDeclarations(), wm) : 0,
//                                    calendarNames,
//                                    calendars );
        return createTrigger(timestamp, item.getTuple(), jh, calendarNames, calendars, new Declaration[][] { delayDeclrs, periodDeclrs }, wm);
    }

    public Trigger createTrigger(long timestamp,
                                 LeftTuple leftTuple,
                                 DefaultJobHandle jh,
                                 String[] calendarNames,
                                 Calendars calendars,
                                 Declaration[][] declrs,
                                 InternalWorkingMemory wm) {
        long timeSinceLastFire = 0;

        Declaration[] delayDeclarations = declrs[0];
        Declaration[] periodDeclarations = declrs[1];

        if ( jh != null ) {
            IntervalTrigger preTrig = (IntervalTrigger) jh.getTimerJobInstance().getTrigger();
            if (preTrig.getLastFireTime() != null) {
                timeSinceLastFire = timestamp - preTrig.getLastFireTime().getTime();
            }
        }


        long newDelay = (delay != null ? evalDelay(leftTuple, delayDeclarations, wm) : 0) - timeSinceLastFire;
        if (newDelay < 0) {
            newDelay = 0;
        }

        return new IntervalTrigger(timestamp,
                                   this.startTime,
                                   this.endTime,
                                   this.repeatLimit,
                                   newDelay,
                                   period != null ? evalPeriod(leftTuple, periodDeclarations, wm) : 0,
                                   calendarNames,
                                   calendars);
    }

    public Trigger createTrigger(long timestamp,
                                 String[] calendarNames,
                                 Calendars calendars) {
        return new IntervalTrigger( timestamp,
                                    this.startTime,
                                    this.endTime,
                                    this.repeatLimit,
                                    0,
                                    0,
                                    calendarNames,
                                    calendars );
    }

    private long evalPeriod(LeftTuple leftTuple, Declaration[] declrs, InternalWorkingMemory wm) {
        Object p = this.period.getValue( leftTuple,  declrs, null, wm );
        if ( p instanceof Number ) {
            return ((Number) p).longValue();
        } else {
            return TimeUtils.parseTimeString( p.toString() );
        }
    }

    private long evalDelay(LeftTuple leftTuple, Declaration[] declrs, InternalWorkingMemory wm) {
        Object d = this.delay.getValue( leftTuple,  declrs, null, wm );
        if ( d instanceof Number ) {
            return ((Number) d).longValue();
        } else {
            return TimeUtils.parseTimeString( d.toString() );
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + delay.hashCode();
        result = prime * result + ((endTime == null) ? 0 : endTime.hashCode());
        result = prime * result + period.hashCode();
        result = prime * result + repeatLimit;
        result = prime * result + ((startTime == null) ? 0 : startTime.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( getClass() != obj.getClass() ) return false;
        ExpressionIntervalTimer other = (ExpressionIntervalTimer) obj;
        if ( delay != other.delay ) return false;
        if ( repeatLimit != other.repeatLimit ) return false;
        if ( endTime == null ) {
            if ( other.endTime != null ) return false;
        } else if ( !endTime.equals( other.endTime ) ) return false;
        if ( period != other.period ) return false;
        if ( startTime == null ) {
            if ( other.startTime != null ) return false;
        } else if ( !startTime.equals( other.startTime ) ) return false;
        return true;
    }

    @Override
    public ConditionalElement clone() {
        return new ExpressionIntervalTimer(startTime,
                                           endTime,
                                           repeatLimit,
                                           delay,
                                           period);
    }
}
