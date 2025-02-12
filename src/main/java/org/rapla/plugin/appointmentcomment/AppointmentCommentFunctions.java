    /*
     * Copyright [2024] [Liselotte Lichtenstein, Christopher Kohlhaas]
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
package org.rapla.plugin.appointmentcomment;


import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.entities.extensionpoints.Function;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.inject.Extension;

import javax.inject.Inject;;
import java.util.List;


@Extension(provides = FunctionFactory.class, id=AppointmentCommentFunctions.NAMESPACE)
public class AppointmentCommentFunctions  implements FunctionFactory{



        static final public String NAMESPACE = "org.rapla.appointment";

        public @Inject AppointmentCommentFunctions(){

        }

        public static void setComment(Appointment appointment, String comment) {
            try {
                Reservation event = appointment.getReservation();
                event.setAnnotation("appointment_comment_" + appointment.getId(), comment);
            } catch (IllegalAnnotationException ex) {
                throw new IllegalStateException(ex);
            }
        }

    public static String getComment(Appointment appointment) {
        return appointment.getReservation().getAnnotation("appointment_comment_" + appointment.getId());
    }


    @Override public Function createFunction(String functionName, List<Function> args) throws IllegalAnnotationException
        {
            if ( functionName.equals(CommentFunction.name))
            {
                return new CommentFunction(args);
            }
            return null;
        }

        private String showComment(Object obj)
        {
            final Appointment appointment;
            if ( obj instanceof AppointmentBlock)
            {
                appointment = ((AppointmentBlock)obj).getAppointment();
            }
            else if (obj instanceof Appointment) {
                appointment = (Appointment) obj;
            } else {
                appointment = null;
            }
            if ( appointment != null ) {
                String comment = getComment(appointment);
                if (comment != null && !comment.trim().isEmpty())
                {
                    return  "\n" + "(" + comment + ")";
                }
            }
            return "";
        }

        class CommentFunction extends Function
        {
            static public final String name = "comment";
            Function arg;

            public CommentFunction( List<Function> args) throws IllegalAnnotationException
            {
                super(NAMESPACE,name, args);
                assertArgs(0,1);
                if ( args.size() > 0)
                {
                    arg = args.get( 0);
                }

            }

            @Override public String eval(EvalContext context)
            {
                final Object obj;
                if ( arg != null)
                {
                    obj = arg.eval( context);
                }
                else
                {
                    obj = context.getFirstContextObject();
                }
                final String result = showComment(obj);
                return result;
            }

        }



}
