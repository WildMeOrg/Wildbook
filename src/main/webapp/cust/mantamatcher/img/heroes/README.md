# Hero Image Carousel

This directory contains the hero images that rotate on the Whiskerbook landing page (index.jsp).

## How to Add New Species Images

1. **Add your image to this directory**
   - Place high-quality images (recommended: 1920x1080 or larger) in this folder
   - Use descriptive filenames (e.g., `elephant.jpg`, `ocelot.jpg`, `african-golden-cat.jpg`)
   - Supported formats: JPG, JPEG, PNG

2. **Update the carousel configuration**
   - Open `/src/main/webapp/index.jsp`
   - Find the `heroImages` array in the JavaScript section (around line 119)
   - Add your new image path to the array:

   ```javascript
   var heroImages = [
       'cust/mantamatcher/img/heroes/jaguar.jpg',
       'cust/mantamatcher/img/heroes/elephant.jpg',
       'cust/mantamatcher/img/heroes/ocelot.jpg'
   ];
   ```

3. **Rebuild the LESS/CSS** (if needed)
   - If you modified any LESS files, recompile them to CSS
   - The carousel uses CSS transitions defined in `_custom.less`

## Current Settings

- **Rotation interval**: 6 seconds (configurable in index.jsp, line 148)
- **Transition effect**: 1 second fade (configurable in _custom.less, line 476)
- **Image position**: Center-center
- **Image sizing**: Cover (image fills entire background while maintaining aspect ratio)

## Tips for Best Results

- Use landscape-oriented images (16:9 aspect ratio works best)
- Ensure images are well-lit and showcase the species clearly
- Consider the text overlay when selecting images (avoid busy backgrounds in the left portion)
- Compress images for web to maintain fast page load times
- Test on different screen sizes to ensure images look good at all resolutions

## Currently Configured Species

1. Jaguar (jaguar.jpg)

Add more species as Whiskerbook expands its coverage!
