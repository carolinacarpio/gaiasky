/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.api;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import gaiasky.render.ComponentTypes;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.camera.NaturalCamera;
import gaiasky.scene.record.RotationComponent;
import gaiasky.util.math.*;
import gaiasky.util.time.ITimeFrameProvider;
import gaiasky.util.tree.OctreeNode;

public interface IFocus {

    /**
     * Whether this focus object actually contains a focus.
     *
     * @return True if the focus is not set, false otherwise.
     */
    boolean isEmpty();

    /**
     * Whether this contains a valid focus object. This returns
     * true in the case of focus views, and only when the focus
     * view is correctly set up and initialized with a valid
     * entity.
     *
     * @return Whether this focus is valid or not.
     */
    boolean isValid();

    /**
     * Returns the unique id of this focus.
     *
     * @return The id.
     */
    long getId();

    /**
     * Returns the id of the focus candidate of this object. Defaults to
     * {@link IFocus#getId()}.
     *
     * @return The id of the candidate.
     */
    long getCandidateId();

    /**
     * Returns the localized name of this focus. If it has no localized name,
     * it returns the default name.
     *
     * @return The localized name.
     */
    String getLocalizedName();

    /**
     * Returns the first name of this focus.
     *
     * @return The first name.
     */
    String getName();

    /**
     * Returns all names of this focus.
     *
     * @return All names of this focus.
     */
    String[] getNames();

    /**
     * Checks whether the focus has the given name.
     *
     * @param name The name.
     * @return True if there is a match.
     */
    boolean hasName(String name);

    /**
     * Checks whether the focus has the given name.
     *
     * @param name      The name.
     * @param matchCase Whether to match the case when comparing.
     * @return True if there is a match.
     */
    boolean hasName(String name, boolean matchCase);

    /**
     * Same as {@link IFocus#getName()}.
     *
     * @return The name.
     */
    String getClosestName();

    /**
     * Returns the name of the focus candidate of this object. Defaults to
     * {@link IFocus#getName()}.
     *
     * @return The name of the candidate.
     */
    String getCandidateName();

    /**
     * Returns the component types of this focus.
     *
     * @return The component types.
     */
    ComponentTypes getCt();

    /**
     * Returns whether this focus object is active or not. The active status
     * determines whether the object can be focussed or not.
     *
     * @return The active status.
     */
    boolean isFocusActive();

    /**
     * Returns the position.
     *
     * @return The position.
     */
    Vector3b getPos();

    /**
     * Gets the first ancestor of this node that is a star.
     *
     * @return The first star ancestor.
     */
    Entity getFirstStarAncestorEntity();

    IFocus getFirstStarAncestor();

    /**
     * Returns the absolute position of this entity in the native coordinates
     * (equatorial system).
     *
     * @param out The out vector.
     * @return The absolute position, same as aux.
     */
    Vector3b getAbsolutePosition(Vector3b out);

    /**
     * Returns the absolute position of the entity identified by
     * name within this entity in the native reference system.
     *
     * @param name The name (lowercase) of the entity to get the position from (useful in case of star groups).
     * @param out  Vector3d to put the return value.
     * @return The absolute position of the entity if it exists, null otherwise.
     */
    Vector3b getAbsolutePosition(String name, Vector3b out);

    /**
     * Same as {@link IFocus#getAbsolutePosition(Vector3b)}.
     *
     * @param out Vector3d where to put the return value.
     * @return The absolute position, same as aux.
     */
    Vector3b getClosestAbsolutePos(Vector3b out);

    /**
     * Gets the position in equatorial spherical coordinates.
     *
     * @return The position in alpha, delta.
     */
    Vector2d getPosSph();

    /**
     * Gets the position of this entity in the next time step in the
     * internal reference system using the given time provider and the given
     * camera.
     *
     * @param aux    The out vector where the result will be stored.
     * @param time   The time frame provider.
     * @param camera The camera.
     * @param force  Whether to force the computation if time is off.
     * @return The aux vector for chaining.
     */
    Vector3b getPredictedPosition(Vector3b aux, ITimeFrameProvider time, ICamera camera, boolean force);

    /**
     * Gets the position of this entity at the given delta time in the future in the
     * internal reference system.
     *
     * @param aux       The out vector where the result will be stored.
     * @param deltaTime Delta time in seconds.
     * @return The aux vector for chaining.
     */
    Vector3b getPredictedPosition(Vector3b aux, double deltaTime);

    /**
     * Returns the current distance to the camera in internal units.
     *
     * @return The current distance to the camera, in internal units.
     */
    double getDistToCamera();

    /**
     * Same as {@link IFocus#getDistToCamera()}.
     *
     * @return The distance to the camera in internal units.
     */
    double getClosestDistToCamera();

    /**
     * Returns the current view angle of this entity, in radians.
     *
     * @return The view angle in radians.
     */
    double getSolidAngle();

    /**
     * Returns the current apparent view angle (view angle corrected with the
     * field of view) of this entity, in radians.
     *
     * @return The apparent view angle in radians.
     */
    double getSolidAngleApparent();

    /**
     * Returns the candidate apparent view angle (view angle corrected with the
     * field of view) of this entity, in radians.
     *
     * @return The apparent view angle in radians.
     */
    double getCandidateSolidAngleApparent();

    /**
     * Returns the right ascension angle of this focus object.
     *
     * @return The right ascension angle in degrees.
     */
    double getAlpha();

    /**
     * Returns the declination angle of this focus object.
     *
     * @return The declination angle in degrees.
     */
    double getDelta();

    /**
     * Returns the size (diameter) of this entity in internal units.
     *
     * @return The size in internal units.
     */
    double getSize();

    /**
     * Returns the radius of this focus object in internal units.
     *
     * @return The radius of the focus, in internal units.
     */
    double getRadius();

    /**
     * Returns the effective temperature.
     * @return The effective temperature of the object, in Kelvins.
     */
    double getTEff();

    /**
     * Returns the surface elevation of the projected position of the current camera
     * on this focus object, which is usually the radius plus a value lookup
     * in the height texture (if exists).
     *
     * @param camPos The camera position.
     * @return The height of the projected position of the current camera.
     */
    double getElevationAt(Vector3b camPos);

    /**
     * Same as {@link #getElevationAt(Vector3b)} but with the option to use the
     * future position of the body instead of the current one.
     *
     * @param camPos            The camera position.
     * @param useFuturePosition Whether to use the future position or the current one.
     * @return The height of the projected position of the current camera on the surface.
     */
    double getElevationAt(Vector3b camPos, boolean useFuturePosition);

    /**
     * Same as {@link #getElevationAt(Vector3b)} but with the option to use the
     * given future position of the body instead of the current one.
     *
     * @param camPos  The camera position.
     * @param nextPos The future position of this body to use.
     * @return The height of the projected position of the current camera on the surface.
     */
    double getElevationAt(Vector3b camPos, Vector3b nextPos);

    /**
     * Returns the height scale of this focus, or 0 if it has no height info.
     *
     * @return The height scale in internal units.
     */
    double getHeightScale();

    /**
     * Gets the apparent magnitude.
     *
     * @return The apparent magnitude.
     */
    float getAppmag();

    /**
     * Gets the absolute magnitude.
     *
     * @return The absolute magnitude.
     */
    float getAbsmag();

    /**
     * Returns the orientation matrix of this focus.
     *
     * @return The orientation matrix. Can be null.
     */
    Matrix4d getOrientation();

    /**
     * Returns the rotation component of this focus.
     *
     * @return The rotation component. Can be null.
     */
    RotationComponent getRotationComponent();

    /**
     * Returns the orientation quaternion of this focus.
     *
     * @return The orientation quaternion. Can be null.
     */
    QuaternionDouble getOrientationQuaternion();

    /**
     * Adds this focus to the hits list if it is hit by the [screenX, screenY]
     * position.
     *
     * @param screenX   The x position of the hit.
     * @param screenY   The y position of the hit.
     * @param w         The viewport width.
     * @param h         The viewport height.
     * @param pixelDist The minimum pixel distance to consider as hit.
     * @param camera    The camera.
     * @param hits      The list where to add the element.
     */
    void addHitCoordinate(int screenX, int screenY, int w, int h, int pixelDist, NaturalCamera camera, Array<IFocus> hits);

    void addEntityHitCoordinate(int screenX, int screenY, int w, int h, int pixelDist, NaturalCamera camera, Array<Entity> hits);

    /**
     * Adds this focus to the hits list if it is hit by the given ray.
     *
     * @param p0     Start point of the ray.
     * @param p1     End point of the ray.
     * @param camera The camera.
     * @param hits   The list where the focus is to be added.
     */
    void addHitRay(Vector3d p0, Vector3d p1, NaturalCamera camera, Array<IFocus> hits);

    void addEntityHitRay(Vector3d p0, Vector3d p1, NaturalCamera camera, Array<Entity> hits);

    /**
     * Hook that runs when the candidate is actually made focus.
     */
    void makeFocus();

    /**
     * Prepares the candidate with the given name.
     *
     * @param name The name in lower case.
     */
    IFocus getFocus(String name);

    /**
     * Checks whether this focus is within its valid time range, so that it can
     * be used as a focus.
     *
     * @return Whether the focus object is within its valid time range.
     */
    boolean isCoordinatesTimeOverflow();

    /**
     * Gets the depth of this focus object in the scene graph.
     *
     * @return The depth of the scene graph.
     */
    int getSceneGraphDepth();

    /**
     * Gets the octant this focus belongs to, if any. This will return null
     * if this focus is not part of an octree.
     *
     * @return The octant this focus belongs to. Null if it is not part of an octree.
     */
    OctreeNode getOctant();

    /**
     * Whether this is a copy or not.
     *
     * @return Whether the object is a copy or not.
     */
    boolean isCopy();

    /**
     * Gets the color of this object.
     *
     * @return The color as an RGBA float array.
     */
    float[] getColor();

    boolean isForceLabel();

    boolean isForceLabel(String name);

}
