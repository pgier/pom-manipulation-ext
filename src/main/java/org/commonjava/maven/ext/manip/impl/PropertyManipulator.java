/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.maven.ext.manip.impl;

import static org.commonjava.maven.ext.manip.util.IdUtils.ga;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.manip.ManipulationException;
import org.commonjava.maven.ext.manip.io.ModelIO;
import org.commonjava.maven.ext.manip.model.Project;
import org.commonjava.maven.ext.manip.state.ManipulationSession;
import org.commonjava.maven.ext.manip.state.PropertyState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Manipulator} implementation that can alter property sections in a project's pom file.
 * Configuration is stored in a {@link PropertyState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Component( role = Manipulator.class, hint = "property-manipulator" )
public class PropertyManipulator
    implements Manipulator
{
    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    @Requirement
    protected ModelIO effectiveModelBuilder;

    @Override
    public void init( final ManipulationSession session )
    {
        final Properties userProps = session.getUserProperties();
        session.setState( new PropertyState( userProps ) );
    }

    /**
     * No prescanning required for Property manipulation.
     */
    @Override
    public void scan( final List<Project> projects, final ManipulationSession session )
        throws ManipulationException
    {
    }

    /**
     * Apply the property changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects, final ManipulationSession session )
        throws ManipulationException
    {
        final PropertyState state = session.getState( PropertyState.class );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( getClass().getSimpleName() + ": Nothing to do!" );
            return Collections.emptySet();
        }

        final Properties overrides = loadRemotePOMProperties( state.getRemotePropertyMgmt(), session );
        final Set<Project> changed = new HashSet<Project>();

        for ( final Project project : projects )
        {
            final Model model = project.getModel();

            if ( overrides.size() > 0 )
            {
                // Only inject the new properties at the top level.
                if ( project.isInheritanceRoot() )
                {
                    logger.info( "Applying property changes to: " + ga( project ) + " with " + overrides );

                    model.getProperties().putAll( overrides );

                    changed.add( project );
                }
                else
                {
                    // For any matching property that exists in the current project overwrite that value.
                    @SuppressWarnings( { "unchecked", "rawtypes" } )
                    Set<String> keyClone = new HashSet(model.getProperties().keySet());
                    keyClone.retainAll( overrides.keySet() );

                    if ( keyClone.size() > 0 )
                    {
                        Iterator<String> keys = keyClone.iterator();
                        while (keys.hasNext())
                        {
                            String matchingKey = keys.next();
                            logger.info( "Overwriting property (" + matchingKey + " in: " + ga( project ) + " with value " + overrides.get( matchingKey ) );
                            model.getProperties().put( matchingKey, overrides.get( matchingKey ) );

                            changed.add( project );
                        }
                    }
                }
            }
        }

        return changed;
    }


    private Properties loadRemotePOMProperties( final List<ProjectVersionRef> remoteMgmt,
                                                final ManipulationSession session )
        throws ManipulationException
    {
        final Properties overrides = new Properties();

        if ( remoteMgmt == null || remoteMgmt.isEmpty() )
        {
            return overrides;
        }

        // Iterate in reverse order so that the first GAV in the list overwrites the last
        final ListIterator<ProjectVersionRef> listIterator = remoteMgmt.listIterator( remoteMgmt.size() );
        while ( listIterator.hasPrevious() )
        {
            final ProjectVersionRef ref = listIterator.previous();
            overrides.putAll( effectiveModelBuilder.getRemotePropertyMappingOverrides( ref, session ) );
        }

        return overrides;
    }
}
