/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.imglib2.render.volume;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.ExtendedRandomAccessibleInterval;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.io.ImgIOException;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.Perspective3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.numeric.ARGBDoubleType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.AbstractARGBDoubleType;
import net.imglib2.type.numeric.NativeARGBDoubleType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;

/**
 * 
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class Renderer
{
	final static int INTERPOL_NN = 0, INTERPOL_NL = 1;
	
	static protected < T extends NumericType< ? > > void render(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< T > target,
			final long minZ,
			final long maxZ,
			final long stepSize,
			final double bg,
			final RowAccumulator< T > accumulator )
	{
		final RandomAccess< T > pixel = target.randomAccess( target );
		final RandomAccess< T > poxel = source.randomAccess();
		
		pixel.setPosition( target.min( 0 ), 0 );
		pixel.setPosition( target.min( 1 ), 0 );

		poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
		poxel.setPosition( pixel.getLongPosition( 1 ), 1 );
		poxel.setPosition( maxZ, 2 );
		
		while ( pixel.getLongPosition( 1 ) <= target.max( 1 ) )
		{
			pixel.setPosition( target.min( 0 ), 0 );
			poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
			while ( pixel.getLongPosition( 0 ) <= target.max( 0 ) )
			{
				poxel.setPosition( maxZ, 2 );
				accumulator.accumulateRow( pixel.get(), poxel, minZ, maxZ, stepSize, 2 );
				
				pixel.fwd( 0 );
				poxel.fwd( 0 );
			}
			
			pixel.fwd( 1 );
			poxel.fwd( 1 );
		}
	}
	
	
	static protected < T extends AbstractARGBDoubleType< T > > void renderARGBDouble(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< ARGBType > target,
			final long minZ,
			final long maxZ,
			final long stepSize,
			final T bg,
			final RowAccumulator< T > accumulator )
	{
		final RandomAccess< ARGBType > pixel = target.randomAccess( target );
		final RandomAccess< T > poxel = source.randomAccess();
		final T accumulate = source.randomAccess().get().createVariable();
		
		pixel.setPosition( target.min( 0 ), 0 );
		pixel.setPosition( target.min( 1 ), 0 );

		poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
		poxel.setPosition( pixel.getLongPosition( 1 ), 1 );
		poxel.setPosition( maxZ, 2 );
		
		while ( pixel.getLongPosition( 1 ) <= target.max( 1 ) )
		{
			pixel.setPosition( target.min( 0 ), 0 );
			poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
			while ( pixel.getLongPosition( 0 ) <= target.max( 0 ) )
			{
				poxel.setPosition( maxZ, 2 );
				accumulate.set( bg.getA(), bg.getR(), bg.getG(), bg.getB() );
				accumulator.accumulateRow( accumulate, poxel, minZ, maxZ, stepSize, 2 );
				pixel.get().set( accumulate.toARGBInt() );
				
				pixel.fwd( 0 );
				poxel.fwd( 0 );
			}
			
			pixel.fwd( 1 );
			poxel.fwd( 1 );
		}
	}
	
	
	final static double accelerate( final double x )
	{
		return 0.5 - 0.5 * Math.cos( Math.PI * x );
	}
	
	final static protected Img< FloatType > floatCopyImagePlus( final ImagePlus imp )
	{
		final Img< FloatType > img;
		final int nPixels = imp.getWidth() * imp.getHeight();
		final long nVoxels =
				( long )nPixels *
				( long )imp.getStack().getSize(); 
		if ( nVoxels > Integer.MAX_VALUE )
			img = new CellImgFactory< FloatType >( 256 ).create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNSlices() },
					new FloatType() );
		else
			img = new ArrayImgFactory< FloatType >().create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNSlices() },
					new FloatType() );
		
		for ( int z = 0; z < imp.getNSlices(); ++z )
		{
			final ImageProcessor ip = imp.getStack().getProcessor( z + 1 );
			final RandomAccessibleInterval< FloatType > slice = Views.hyperSlice( img, 2, z );
			
			final Cursor< FloatType > cursor = Views.flatIterable( slice ).cursor(); 
			for ( int i = 0; i < nPixels; ++i )
				cursor.next().set( ip.getf( i ) );
		}
		return img;
	}
	
	final static protected Img< FloatType > floatCopyCompositeImage( final ImagePlus imp )
	{
		final Img< FloatType > img;
		final int nPixels = imp.getWidth() * imp.getHeight();
		final long nVoxels =
				( long )nPixels *
				( long )imp.getStack().getSize(); 
		if ( nVoxels > Integer.MAX_VALUE )
			img = new CellImgFactory< FloatType >( 256 ).create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNChannels(),
							imp.getNSlices() },
					new FloatType() );
		else
			img = new ArrayImgFactory< FloatType >().create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNChannels(),
							imp.getNSlices() },
					new FloatType() );
		
		for ( int c = 0; c < imp.getNChannels(); ++c )
		{
			final RandomAccessibleInterval< FloatType > channel = Views.hyperSlice( img, 2, c );
			for ( int z = 0; z < imp.getNSlices(); ++z )
			{
				final ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex( c + 1, z + 1, 1 ) );
				final RandomAccessibleInterval< FloatType > slice = Views.hyperSlice( channel, 2, z );
				
				final Cursor< FloatType > cursor = Views.flatIterable( slice ).cursor(); 
				for ( int i = 0; i < nPixels; ++i )
					cursor.next().set( ip.getf( i ) );
			}
		}
		return img;
	}
	
	
	final static Img< NativeARGBDoubleType > convert(
			final RandomAccessible< RealComposite< FloatType > > composite,
			final Interval box,
			final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble )
	{
		final RandomAccessible< ARGBDoubleType > argbComposite = Converters.convert(
				composite,
				composite2ARGBDouble,
				new ARGBDoubleType() );
		
		/* copy it as on-the-fly conversion isn't the quickest thing in the world */
		final Img< NativeARGBDoubleType > argbCopy;
		if ( box.dimension( 0 ) * box.dimension( 1 ) * box.dimension( 2 ) * 4 > Integer.MAX_VALUE )
			argbCopy = new CellImgFactory< NativeARGBDoubleType >( 256 ).create( box, new NativeARGBDoubleType() );
		else
			argbCopy = new ArrayImgFactory< NativeARGBDoubleType >().create( box, new NativeARGBDoubleType() );
		
		final IterableInterval< ARGBDoubleType > sourceIterable = Views.flatIterable( Views.interval( argbComposite, box ) );
		final IterableInterval< NativeARGBDoubleType > targetIterable = Views.flatIterable( Views.interval( argbCopy, box ) );
		final Cursor< ARGBDoubleType > sourceCursor = sourceIterable.cursor();
		final Cursor< NativeARGBDoubleType > targetCursor = targetIterable.cursor();
		while ( targetCursor.hasNext() )
			targetCursor.next().set( sourceCursor.next() );
		
		return argbCopy;
	}
	
	
	final static protected AffineTransform3D buildAffineTransform(
			final AffineGet orientation,
			final double animation,
			final long width,
			final long height,
			final long depth )
	{
		final double l = accelerate( animation );
		
		/* rotation */
		final AffineTransform3D centerShift = new AffineTransform3D();
		centerShift.set(
				1, 0, 0, -width / 2.0,
				0, 1, 0, -height / 2.0,
				0, 0, 1, -depth / 2.0 );
		
		final double f = height;
		
		final AffineTransform3D zShift = new AffineTransform3D();
		zShift.set(
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, depth / 2.0 + f );
		
		final AffineTransform3D affine = new AffineTransform3D();
		final AffineTransform3D rotation = new AffineTransform3D();
		rotation.rotate( 0, -l * Math.PI * 2 * 2 );
		rotation.rotate( 1, animation * Math.PI * 2 );
		
		affine.preConcatenate( centerShift );
		affine.preConcatenate( orientation );
		affine.preConcatenate( rotation );
		affine.preConcatenate( zShift );
		
		return affine;
	}
	
	final static protected void appendCamera(
			final InvertibleRealTransformSequence transformSequence,
			final long width,
			final long height,
			final long depth )
	{
		final AffineTransform3D centerUnshiftXY = new AffineTransform3D();
		centerUnshiftXY.set(
				1, 0, 0, width / 2.0,
				0, 1, 0, height / 2.0,
				0, 0, 1, 0 );
		
		/* camera */
		final double f = height;
		final Perspective3D perspective = Perspective3D.getInstance();
		final Scale scale = new Scale( f, f, 1 );
		
		/* add all to sequence */
		transformSequence.add( perspective );
		transformSequence.add( scale );
		transformSequence.add( centerUnshiftXY );
	}
	
	final static protected < T extends NumericType< T > > RandomAccessible< T > buildTransformedSource(
			final RandomAccessible< T > source,
			final InvertibleRealTransform transform,
			final int interpolationMethod )
	{
		final RealRandomAccessible< T > interpolant;
		switch ( interpolationMethod )
		{
			case INTERPOL_NL:
				interpolant = Views.interpolate( source, new NLinearInterpolatorFactory< T >() );
				break;
			default:
				interpolant = Views.interpolate( source, new NearestNeighborInterpolatorFactory< T >() );
		}
		
//		ImageJFunctions.show( Views.interval( Views.raster( RealViews.transform( interpolant, transform ) ), new long[]{ 0, 0, 390 / 2 }, new long[]{ 928, 390, 390 + 390 / 2} ) );
		return RealViews.transform( interpolant, transform );
	}
	
	final static protected < T extends NumericType< T > > RandomAccessible< T > buildTransformedSource(
			final RandomAccessibleInterval< T > source,
			final InvertibleRealTransform transform,
			final int interpolationMethod )
	{
		final ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > extendedImg = Views.extendValue( source, source.randomAccess().get().createVariable() );
		return buildTransformedSource( extendedImg, transform, interpolationMethod );
	}
	
	
	/**
	 * Create an AlphaIntensity rendering of a 3D stack.  No composites or
	 * time series supported.
	 *  
	 * @param impSource 3d image, will be converted to singel channle float even if it is ARGB-color
	 * @param width width of the target canvas
	 * @param height height of the target canvas
	 * @param min minimum intensity
	 * @param max maximum intensity
	 * @param orientation initial transformation assuming that the 3d volume is centered (e.g. export of Interactive Stack Rotation)
	 * @param animation a value between 0 and 1 that specifies the camera position along a predefined path
	 * @param stepSize z-stepping for the volume renderer higher is faster but less beautiful
	 * @param bg background intensity
	 * @param interpolationMethod 0 NN, 1 NL
	 * @param alphaScale scale factor for linear intensity to alpha transfer 
	 * @param alphaOffset offset for linear intensity to alpha transfer
	 * 
	 * @return
	 */
	final static public ImagePlus runGray(
			final ImagePlus impSource,
			final int width,
			final int height,
			final double min,
			final double max,
			final AffineTransform3D orientation,
			final double animation,
			final int stepSize,
			final double bg,
			final int interpolationMethod,
			final double alphaScale,
			final double alphaOffset )
	{
		/* copy contents into most appropriate container */
		final Img< FloatType > img = floatCopyImagePlus( impSource );
		
		System.out.println(
				img.dimension( 0 ) + " " + 
				img.dimension( 1 ) + " " +
				img.dimension( 2 ) );
		
		/* build transformation */
		final AffineTransform3D affine = buildAffineTransform(
				orientation,
				animation,
				img.dimension( 0 ),
				img.dimension( 1 ),
				img.dimension( 2 ) );
		
		final InvertibleRealTransformSequence transformSequence = new InvertibleRealTransformSequence();
		
		transformSequence.add( affine );
		
		appendCamera(
				transformSequence,
				width,
				height,
				img.dimension( 2 ) );
		
		/* build source */
		final RandomAccessible< FloatType > rotated = buildTransformedSource( img, transformSequence, interpolationMethod );
		
		/* accumulator */
		final AlphaIntensityLayers< FloatType > accumulator = new AlphaIntensityLayers< FloatType >( alphaScale, alphaOffset );
		
		/* calculate boundaries */
		final FinalRealInterval bounds = affine.estimateBounds( img );
		final long minZ	= ( long )Math.floor( bounds.realMin( 2 ) );
		final long maxZ	= ( long )Math.ceil( bounds.realMax( 2 ) );
		
		/* build target */
		final float[] floatPixels = new float[ width * height ];
		final ArrayImg< FloatType, FloatArray > floatCanvas = ArrayImgs.floats( floatPixels, width, height );
		
		/* render */
		render( rotated, floatCanvas, minZ, maxZ, stepSize, bg, accumulator );
		
		final FloatProcessor fp = new FloatProcessor( width, height, floatPixels );
		new ImagePlus( impSource.getTitle(), fp ).show();
		fp.setMinAndMax( min, max );
		final ByteProcessor bp = ( ByteProcessor )fp.convertToByte( true );
		
		return new ImagePlus( impSource.getTitle(), bp );
	}
	
	
	/**
	 * Create an AlphaIntensity rendering of a 3D stack.  No composites or
	 * time series supported.
	 *  
	 * @param impSource 3d image, will be converted to singel channle float even if it is ARGB-color
	 * @param width width of the target canvas
	 * @param height height of the target canvas
	 * @param orientation initial transformation assuming that the 3d volume is centered (e.g. export of Interactive Stack Rotation)
	 * @param animation a value between 0 and 1 that specifies the camera position along a predefined path
	 * @param stepSize z-stepping for the volume renderer higher is faster but less beautiful
	 * @param bg background color
	 * @param interpolationMethod 0 NN, 1 NL
	 * @param alphaScale scale factor for linear intensity to alpha transfer 
	 * @param alphaOffset offset for linear intensity to alpha transfer
	 * 
	 * @return
	 */
	final static public < T extends AbstractARGBDoubleType< T > > ImagePlus runARGB(
			final ImagePlus impSource,
			final int width,
			final int height,
			final AffineTransform3D orientation,
			final double animation,
			final long stepSize,
			final T bg,
			final int interpolationMethod,
			final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble )
	{
		/* copy contents into most appropriate container */
		final Img< FloatType > img = floatCopyCompositeImage( impSource );
		
		/* collapse composite dimension */
		final RandomAccessibleInterval< FloatType > xyzc = Views.permute( img, 2, 3 );
		final CompositeView< FloatType, RealComposite< FloatType > > composite =
				Views.collapseReal( Views.extendZero( xyzc ), ( int )xyzc.dimension( 3 ) );
		
		final FinalInterval box = new FinalInterval(
				xyzc.dimension( 0 ),
				xyzc.dimension( 1 ),
				xyzc.dimension( 2 ) );
		
		System.out.println(
				box.dimension( 0 ) + " " + 
				box.dimension( 1 ) + " " +
				box.dimension( 2 ) );
		
		/* convert */
		final Img< NativeARGBDoubleType > argbCopy = convert(
				composite,
				box,
				composite2ARGBDouble );
		
		/* build transformation */
		final AffineTransform3D affine = buildAffineTransform(
				orientation,
				animation,
				xyzc.dimension( 0 ),
				xyzc.dimension( 1 ),
				xyzc.dimension( 2 ) );
		
		final InvertibleRealTransformSequence transformSequence = new InvertibleRealTransformSequence();
		
		transformSequence.add( affine );
		
		appendCamera(
				transformSequence,
				width,
				height,
				xyzc.dimension( 2 ) );
		
		/* build source */
		final RandomAccessible< NativeARGBDoubleType > rotated = buildTransformedSource( argbCopy, transformSequence, interpolationMethod );
		
		/* calculate boundaries */
		final FinalRealInterval bounds = affine.estimateBounds( box );
		final long minZ	= ( long )Math.floor( bounds.realMin( 2 ) );
		final long maxZ	= ( long )Math.ceil( bounds.realMax( 2 ) );
		
		/* build target */
		final int[] argbPixels = new int[ width * height ];
		final ArrayImg< ARGBType, IntArray > argbCanvas = ArrayImgs.argbs( argbPixels, width, height );
		
		/* accumulator */
		final ARGBDoubleLayers< NativeARGBDoubleType > accumulator = new ARGBDoubleLayers< NativeARGBDoubleType >();
		
		final NativeARGBDoubleType nativeBg = new NativeARGBDoubleType();
		nativeBg.set( bg.getA(), bg.getR(), bg.getG(), bg.getB() );
		
		/* render */
		renderARGBDouble( rotated, argbCanvas, minZ, maxZ, stepSize, nativeBg, accumulator );
		
		final ColorProcessor cp = new ColorProcessor( width, height, argbPixels );
		
		return new ImagePlus( impSource.getTitle(), cp );
	}
	
	final static public void main( final String[] args ) throws ImgIOException
	{
		new ImageJ();
		
		/* gray scale */
		final ImagePlus imp = new ImagePlus( "/home/saalfeld/tmp/valia/2.tif" );
		final ImagePlus omp = runGray(
				imp,
				imp.getWidth(),
				imp.getHeight(),
				0,
				0.03,
				new AffineTransform3D(),
				0,
				1,
				1,
				0,
				1.0 / 0.1,
				-0.003 );
		
		omp.show();
		
		
		/* color */
		final ImagePlus imp2 = new ImagePlus( "/home/saalfeld/examples/l1-cns-05-05-5-DPX-9.tif" );
		
		final ARGBDoubleType bgARGB = new ARGBDoubleType( 1, 0, 0, 0 );
		
		final double s = 2.0 / 4095.0;
		final double a = 1.0;
		
		final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble =
				new RealCompositeARGBDoubleConverter< FloatType >( imp2.getNChannels() );
		
		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, s, 0, 0 ), 0 );
		composite2ARGBDouble.setARGB( new ARGBDoubleType( 0.35 * a, s, s, s ), 1 );
		composite2ARGBDouble.setARGB( new ARGBDoubleType( 0, s, s, s ), 2 );
		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, 0, s, 0 ), 3 );
		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, 0, 0, s ), 4 );
		
		final ImagePlus omp2 = runARGB(
				imp2,
				imp2.getWidth(),
				imp2.getHeight(),
				new AffineTransform3D(),
				0,
				1,
				bgARGB,
				0,
				composite2ARGBDouble );
		
		omp2.show();
	}
}
