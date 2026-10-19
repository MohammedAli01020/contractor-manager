# المرحلة 2: البيانات الأساسية

كل المسارات تحت /api/v1 وتتطلب تسجيل الدخول. كل قوائم الصفحات تقبل page (يبدأ من 0) وsize (حتى 100).

| المورد | المسارات | فلاتر القائمة |
|---|---|---|
| المواقع | /sites | q, includeInactive |
| السائقون والعمال | /drivers | q (اسم أو هاتف), type (DRIVER/WORKER), includeInactive |
| أنواع المعدات | /equipment-types | includeInactive (قائمة كاملة بلا ترقيم) |
| المعدات | /equipment | q (كود أو اسم), typeId, status, includeInactive |

لكل مورد: GET (قائمة) / GET {id} / POST (201) / PUT {id} / DELETE {id} (تعطيل، 204).

## قواعد
- PUT يتطلب حقل version من آخر قراءة. غير المطابق يعطي 409.
- DELETE تعطيل فقط. السجل يبقى ويظهر مع includeInactive=true، ويُعاد تفعيله بـ PUT مع active=true.
- المبالغ بخانتين عشريتين، والسالب مرفوض (في الـ API وفي قاعدة البيانات).
- اسم الموقع واسم نوع المعدة وكود المعدة فريدة داخل المقاول (بغض النظر عن حالة الأحرف).
- نوع المعدة المرتبط بمعدات فعّالة لا يُعطَّل.
- حالة المعدة: AVAILABLE, MAINTENANCE, OUT_OF_SERVICE.
