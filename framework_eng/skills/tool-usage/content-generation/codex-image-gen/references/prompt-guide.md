# Prompt guide for `image_generation` (via codex-image-gen wrapper)

> The base source is the GPT-5 team’s recommendations for working with the `image_generation` tool.
> Adaptation: section 0, "What the wrapper does for you," was added so agents do not duplicate technical instructions.

## 0. What the wrapper does for you (read first)

`codex_image_gen.py` automatically appends the following to the end of your prompt:

1. **Size hint:** `Use 1024x1024 unless the user explicitly asked for another size.`
2. **Save command:** `Save the resulting image as <filename> in the current working directory. Do not save under any other name and do not write outside the current working directory.`
3. **List of reference files** (if you passed `--reference-image`) - they are already copied into the CWD under names like `ref_0.png`, `ref_1.jpg`, etc. You can refer to them in the prompt by their short names.

Therefore, in your own prompt, do NOT duplicate: the filename, save path, reference paths, or the default size. Focus on the image content.

## 1. General principle

A good prompt describes not an idea, but the observable result:
- what exactly should be in the frame
- in what style
- from what angle
- under what light
- with what constraints

Bad version:

```text
Сделай красиво
```

Good version:

```text
Clean editorial-style product photo of a ceramic coffee mug, centered, three-quarter view, soft studio lighting, warm neutral palette, subtle shadow, no text, no logo, no extra objects.
```

## 2. What to specify in a prompt for generating a new image

Core axes:
- Subject: who or what is shown.
- Composition: close-up, wide shot, top-down, front-facing, centered, asymmetrical.
- Style: photo, illustration, 3D render, flat vector, watercolor, poster, pixel art, UI mockup.
- Light: daylight, studio light, dramatic light, overcast, neon, golden hour.
- Color: muted, pastel, saturated, monochrome, warm, cold.
- Detail: minimal, clean, highly detailed, realistic textures.
- Background: transparent, plain white, city street, office interior, gradient backdrop.
- Constraints: no text, no watermark, no extra hands, no background people.

Practical recommendations:
- Specify the result type immediately: `photo`, `illustration`, `3D render`, `diagram`, `mockup`.
- Be specific: not `modern office`, but `bright open-plan office with oak desks and glass walls`.
- If quality matters, specify the level of cleanliness: `clean`, `polished`, `high legibility`, `minimal clutter`.
- If you need an object without surroundings, say so explicitly: `isolated on white background` or `transparent background`.
- If you need multiple objects, specify the exact count.
- If text is not needed in the image, explicitly say `no text`.
- Do not overload the prompt with dozens of equally weighted requirements. Better 5-8 priority characteristics.

## 3. What to specify in a prompt for editing an existing image

For editing, it is important not to restate the whole image, but to describe the delta of changes.

Good structure:
- What to keep: composition, pose, style, lighting, background, proportions.
- What to change: color, object, background, clothing, facial expression, size, material.
- What to remove: extra people, text, logo, noise, artifacts.
- What to add: new object, caption, accessory, shadow, background, props.
- How carefully to change it: `keep everything else unchanged`.

Recommendations for editing:
- Start with a phrase like `Keep the original composition and lighting`.
- Change one semantic block at a time if you need a predictable result.
- If the edit is local, specify the exact area: `change only the background`, `replace the shirt color only`.
- If the original style must be preserved, say so explicitly: `preserve the original illustration style`.
- If you remove an object, it helps to specify what should fill the space: `remove the person and fill the area with the same wall texture`.
- If you change the background, clarify whether shadows and light direction should be preserved.
- If you need a retouch result, add: `natural look`, `seamless edit`, `realistic blending`.
- If the face, proportions, or pose must not be touched, say this separately.

When working through the wrapper, pass the original via `--reference-image <path>`. In the prompt, refer to it by the name `ref_0.<ext>` (see section 0).

## 4. Handy templates

### 4.1. Template for generating a new image

```text
[Image type] showing [main subject], [composition/view], in [style], with [lighting], [color palette], [detail level]. Background: [background]. Constraints: [what to avoid].
```

Example:

```text
Product photo showing a matte black wireless mouse, top-down view, in a premium commercial style, with soft studio lighting, neutral gray palette, high detail. Background: clean light gray seamless backdrop. Constraints: no text, no logo, no extra objects.
```

### 4.2. Template for editing an existing image

```text
Use ref_0.<ext> as the source. Keep [what must stay unchanged]. Change [specific change]. Remove [what to remove]. Add [what to add if needed]. Preserve [style/lighting/proportions/background if important]. Make the edit look [natural / seamless / realistic / clean].
```

Example:

```text
Use ref_0.png as the source. Keep the original composition, pose, and lighting. Change the jacket color from blue to dark green. Remove the background person on the left. Preserve the original photo realism and skin tones. Make the edit look seamless and natural.
```

## 5. 10 ready-made templates for generating a new image

### 1. Product photo

```text
Commercial product photo of [object], centered, three-quarter view, soft studio lighting, premium minimal aesthetic, realistic materials, subtle shadow, plain light background, no text, no extra objects.
```

### 2. Portrait

```text
Realistic portrait of [person description], chest-up framing, looking at camera, soft natural light, shallow depth of field, clean background, natural skin texture, calm expression, no text, no watermark.
```

### 3. Lifestyle scene

```text
Lifestyle photo of [subject] in [environment], candid moment, natural daylight, balanced composition, realistic details, warm and inviting atmosphere, no text, no extra visual clutter.
```

### 4. Office / business illustration

```text
Clean modern illustration of [business scene], isometric or front-facing composition, flat vector style, limited corporate color palette, high clarity, minimal clutter, white or very light background, no text.
```

### 5. UI / app mockup

```text
High-fidelity UI mockup for [app/screen], clean layout, excellent legibility, structured spacing, modern visual hierarchy, realistic device frame or flat presentation, no branding, no watermark.
```

### 6. Icon / sticker

```text
Simple icon-style illustration of [object], centered, bold silhouette, clean edges, flat colors, transparent background, no text, no shadow unless specified.
```

### 7. Poster / cover art

```text
Bold poster design featuring [subject], dramatic composition, striking typography area left empty, high contrast palette, cinematic lighting, polished graphic style, no actual text rendered.
```

### 8. Architectural interior

```text
Photorealistic interior rendering of [room], wide-angle view, balanced composition, natural daylight from windows, realistic materials, tasteful styling, clean and uncluttered, no people.
```

### 9. Concept art / fantasy scene

```text
Atmospheric concept art of [scene], wide cinematic shot, dramatic lighting, rich environmental details, cohesive color palette, epic mood, highly detailed, no text, no watermark.
```

### 10. Diagram / explainer visual

```text
Minimal explainer-style visual of [concept], clean composition, simple shapes, clear separation of elements, restrained palette, white background, high readability, no decorative clutter, no extra text unless explicitly required.
```

## 6. 10 ready-made templates for editing an existing image

### 1. Change color

```text
Use ref_0.<ext> as the source. Keep the original composition, lighting, and materials. Change the color of [object] to [new color]. Preserve everything else unchanged. Make the edit seamless and realistic.
```

### 2. Remove object

```text
Use ref_0.<ext> as the source. Remove [object/person] from the image. Fill the area naturally using the surrounding background and textures. Keep the original lighting, perspective, and overall scene unchanged.
```

### 3. Replace background

```text
Use ref_0.<ext> as the source. Keep the main subject unchanged. Replace the background with [new background]. Match the original lighting direction and perspective. Make the result look natural and well integrated.
```

### 4. Add object

```text
Use ref_0.<ext> as the source. Keep the original image style and composition. Add [object] in [location]. Match scale, lighting, perspective, and shadows so the new element blends naturally into the scene.
```

### 5. Retouch portrait

```text
Use ref_0.<ext> as the source. Keep the person recognizable and preserve natural skin texture. Remove minor blemishes, reduce under-eye shadows slightly, and clean up stray hairs. Do not over-smooth the face. Keep lighting and expression unchanged.
```

### 6. Change clothing

```text
Use ref_0.<ext> as the source. Keep the person’s pose, face, body proportions, and background unchanged. Replace the current clothing with [new clothing description]. Preserve realistic folds, lighting, and fabric texture.
```

### 7. Extend / uncrop image

```text
Use ref_0.<ext> as the source. Extend the image beyond its current borders in the same style and perspective. Continue the background naturally and keep the subject unchanged. Make the expansion seamless.
```

### 8. Convert style

```text
Use ref_0.<ext> as the source. Preserve the original composition and subject. Transform the image into [target style: watercolor / comic / 3D render / flat illustration]. Keep the scene recognizable and visually coherent.
```

### 9. Clean up image

```text
Use ref_0.<ext> as the source. Remove distracting elements, visual noise, artifacts, and unwanted small objects. Keep the main subject, framing, and lighting unchanged. Make the image cleaner without changing its meaning.
```

### 10. Add text area without rendering text

```text
Use ref_0.<ext> as the source. Keep the main subject and overall style. Recompose or simplify [top/bottom/left/right] area to create clean negative space for future text placement. Do not render any actual text.
```

## 7. Common mistakes

- Too abstract: `make it stylish`.
- No style specified.
- No angle specified.
- No constraints, which causes extra objects to appear.
- For editing, not saying what exactly needs to be preserved.
- Trying to make too many unrelated changes in one step.
- Duplicating wrapper instructions (filename, save path) - the wrapper will insert them itself, see section 0.
